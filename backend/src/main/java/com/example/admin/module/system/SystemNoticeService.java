package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.entity.SysNoticeDO;
import com.example.admin.module.system.mapper.SysNoticeMapper;
import com.example.admin.module.system.mapper.SysNoticeReadMapper;
import com.example.admin.module.system.entity.SysNoticeReadDO;
import com.example.admin.security.SecurityUtils;
import com.example.admin.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemNoticeService {

    private static final int PUBLISHED = 1;
    private static final int MIN_LATEST_LIMIT = 1;
    private static final int MAX_LATEST_LIMIT = 20;

    private final SysNoticeMapper noticeMapper;
    private final SysNoticeReadMapper noticeReadMapper;
    private final NoticeSseService noticeSseService;

    public PageResult<SysNoticeDO> page(long pageNum, long pageSize, String title, Integer type) {
        Page<SysNoticeDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysNoticeDO> wrapper = new LambdaQueryWrapper<SysNoticeDO>()
                .like(StringUtils.hasText(title), SysNoticeDO::getNoticeTitle, title)
                .eq(type != null, SysNoticeDO::getNoticeType, type)
                .orderByDesc(SysNoticeDO::getId);
        IPage<SysNoticeDO> result = noticeMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public List<SysNoticeDO> latest(int limit) {
        return noticeMapper.selectList(new LambdaQueryWrapper<SysNoticeDO>()
                .eq(SysNoticeDO::getStatus, PUBLISHED)
                .orderByDesc(SysNoticeDO::getId)
                .last("LIMIT " + Math.min(Math.max(limit, MIN_LATEST_LIMIT), MAX_LATEST_LIMIT)));
    }

    public Long create(SysNoticeDO notice) {
        notice.setId(null);
        notice.setCreatedBy(SecurityUtils.tryGetUserId());
        noticeMapper.insert(notice);
        noticeSseService.publishAll(notice);
        return notice.getId();
    }

    public void update(SysNoticeDO notice) {
        if (notice.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "公告 ID 不能为空");
        }
        noticeMapper.updateById(notice);
        noticeSseService.publishAll(notice);
    }

    public void delete(Long id) {
        noticeMapper.deleteById(id);
    }

    public long unreadCount(Long userId) {
        long total = noticeMapper.selectCount(new LambdaQueryWrapper<SysNoticeDO>()
                .eq(SysNoticeDO::getStatus, PUBLISHED));
        long read = noticeReadMapper.selectCount(new LambdaQueryWrapper<SysNoticeReadDO>()
                .eq(SysNoticeReadDO::getUserId, userId));
        return Math.max(total - read, 0);
    }

    public void markRead(Long userId, Long noticeId) {
        noticeReadMapper.markRead(TenantContext.getTenantId(), userId, noticeId);
    }

    public void markAllRead(Long userId) {
        noticeReadMapper.markAllRead(TenantContext.getTenantId(), userId);
    }

}

