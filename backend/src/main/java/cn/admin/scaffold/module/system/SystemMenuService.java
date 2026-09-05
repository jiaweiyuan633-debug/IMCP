package cn.admin.scaffold.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.module.system.dto.MenuSaveRequest;
import cn.admin.scaffold.module.system.entity.SysMenuDO;
import cn.admin.scaffold.module.system.mapper.SysMenuMapper;
import cn.admin.scaffold.module.system.vo.MenuVo;
import cn.admin.scaffold.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SystemMenuService {

    private static final long ROOT_PARENT_ID = 0L;
    private static final int DEFAULT_SORT = 0;
    private static final int VISIBLE = 1;
    private static final int ENABLED = 1;

    private final SysMenuMapper menuMapper;
    private final TokenService tokenService;

    public List<MenuVo> tree() {
        List<SysMenuDO> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenuDO>()
                .orderByAsc(SysMenuDO::getSort)
                .orderByAsc(SysMenuDO::getId));
        return buildTree(menus, ROOT_PARENT_ID);
    }

    public Long create(MenuSaveRequest request) {
        SysMenuDO menu = toEntity(request);
        // 菜单 id 动态化：id 由数据库自增统一分配，不接受前端/迁移脚本指定。
        // 此前迁移靠手工维护 id 区间（如 V51 注释「当前最大 165，新增 166~175」），
        // 区间错位/并发插入易冲突覆盖；改用 perm 唯一键定位后，创建方无 id 语义。
        menu.setId(null);
        menuMapper.insert(menu);
        return menu.getId();
    }

    @Transactional
    public void update(MenuSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "菜单 ID 不能为空");
        }
        SysMenuDO existing = menuMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        SysMenuDO updated = toEntity(request);
        menuMapper.updateById(updated);
        // 仅权限编码（perm）变更才需清权限缓存——名称/图标/排序等改动不影响已缓存的权限集合，
        // 且权限编码变更影响所有绑定该菜单的用户、无法精确反查，全清最安全（提交后执行防提交前竞态重缓存）
        if (!Objects.equals(existing.getPerm(), updated.getPerm())) {
            tokenService.evictAllPermissionsAfterCommit();
        }
    }

    @Transactional
    public void delete(Long id) {
        menuMapper.deleteById(id);
        menuMapper.delete(new LambdaQueryWrapper<SysMenuDO>().eq(SysMenuDO::getParentId, id));
        // 菜单被删除则其权限编码随之消失，所有绑定用户需立即失效缓存，否则已删除权限残留至 TTL
        tokenService.evictAllPermissionsAfterCommit();
    }

    private SysMenuDO toEntity(MenuSaveRequest request) {
        SysMenuDO menu = new SysMenuDO();
        menu.setId(request.getId());
        menu.setParentId(request.getParentId() == null ? Long.valueOf(ROOT_PARENT_ID) : request.getParentId());
        menu.setName(request.getName());
        menu.setType(request.getType());
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setPerm(request.getPerm());
        menu.setIcon(request.getIcon());
        menu.setSort(request.getSort() == null ? Integer.valueOf(DEFAULT_SORT) : request.getSort());
        menu.setVisible(request.getVisible() == null ? Integer.valueOf(VISIBLE) : request.getVisible());
        menu.setStatus(request.getStatus() == null ? Integer.valueOf(ENABLED) : request.getStatus());
        return menu;
    }

    private List<MenuVo> buildTree(List<SysMenuDO> menus, Long parentId) {
        List<MenuVo> result = new ArrayList<>();
        for (SysMenuDO menu : menus) {
            if (parentId.equals(menu.getParentId())) {
                result.add(toVo(menu, buildTree(menus, menu.getId())));
            }
        }
        return result;
    }

    private MenuVo toVo(SysMenuDO menu, List<MenuVo> children) {
        return MenuVo.builder()
                .id(menu.getId())
                .parentId(menu.getParentId())
                .name(menu.getName())
                .type(menu.getType())
                .path(menu.getPath())
                .component(menu.getComponent())
                .perm(menu.getPerm())
                .icon(menu.getIcon())
                .sort(menu.getSort())
                .visible(menu.getVisible())
                .status(menu.getStatus())
                .children(children)
                .build();
    }
}

