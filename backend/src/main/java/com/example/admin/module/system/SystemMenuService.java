package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.dto.MenuSaveRequest;
import com.example.admin.module.system.entity.SysMenu;
import com.example.admin.module.system.mapper.SysMenuMapper;
import com.example.admin.module.system.vo.MenuVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemMenuService {

    private static final long ROOT_PARENT_ID = 0L;
    private static final int DEFAULT_SORT = 0;
    private static final int VISIBLE = 1;
    private static final int ENABLED = 1;

    private final SysMenuMapper menuMapper;

    public List<MenuVo> tree() {
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));
        return buildTree(menus, ROOT_PARENT_ID);
    }

    public Long create(MenuSaveRequest request) {
        SysMenu menu = toEntity(request);
        menuMapper.insert(menu);
        return menu.getId();
    }

    public void update(MenuSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "菜单 ID 不能为空");
        }
        if (menuMapper.selectById(request.getId()) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        menuMapper.updateById(toEntity(request));
    }

    public void delete(Long id) {
        menuMapper.deleteById(id);
        menuMapper.delete(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
    }

    private SysMenu toEntity(MenuSaveRequest request) {
        SysMenu menu = new SysMenu();
        menu.setId(request.getId());
        menu.setParentId(request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId());
        menu.setName(request.getName());
        menu.setType(request.getType());
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setPerm(request.getPerm());
        menu.setIcon(request.getIcon());
        menu.setSort(request.getSort() == null ? DEFAULT_SORT : request.getSort());
        menu.setVisible(request.getVisible() == null ? VISIBLE : request.getVisible());
        menu.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        return menu;
    }

    private List<MenuVo> buildTree(List<SysMenu> menus, Long parentId) {
        List<MenuVo> result = new ArrayList<>();
        for (SysMenu menu : menus) {
            if (parentId.equals(menu.getParentId())) {
                result.add(toVo(menu, buildTree(menus, menu.getId())));
            }
        }
        return result;
    }

    private MenuVo toVo(SysMenu menu, List<MenuVo> children) {
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

