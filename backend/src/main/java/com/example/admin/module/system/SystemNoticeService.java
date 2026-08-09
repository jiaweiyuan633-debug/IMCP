package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.entity.SysNotice;
import com.example.admin.module.system.mapper.SysNoticeMapper;
import com.example.admin.module.system.mapper.SysNoticeReadMapper;
import com.example.admin.module.system.entity.SysNoticeRead;
import com.example.admin.security.SecurityUtils;
import com.example.admin.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemNoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysNoticeReadMapper noticeReadMapper;
    private final NoticeSseService noticeSseService;

    public PageResult<SysNotice> page(long pageNum, long pageSize, String title, Integer type) {
        Page<SysNotice> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<SysNotice>()
                .like(StringUtils.hasText(title), SysNotice::getNoticeTitle, title)
                .eq(type != null, SysNotice::getNoticeType, type)
                .orderByDesc(SysNotice::getId);
        IPage<SysNotice> result = noticeMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public List<SysNotice> latest(int limit) {
        return noticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, 1)
                .orderByDesc(SysNotice::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 20)));
    }

    public Long create(SysNotice notice) {
        notice.setId(null);
        notice.setCreatedBy(tryGetUserId());
        noticeMapper.insert(notice);
        noticeSseService.publishAll(notice);
        return notice.getId();
    }

    public void update(SysNotice notice) {
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
        long total = noticeMapper.selectCount(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, 1));
        long read = noticeReadMapper.selectCount(new LambdaQueryWrapper<SysNoticeRead>()
                .eq(SysNoticeRead::getUserId, userId));
        return Math.max(total - read, 0);
    }

    public void markRead(Long userId, Long noticeId) {
        noticeReadMapper.markRead(TenantContext.getTenantId(), userId, noticeId);
    }

    public void markAllRead(Long userId) {
        noticeReadMapper.markAllRead(TenantContext.getTenantId(), userId);
    }

    private Long tryGetUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception exception) {
            return null;
        }
    }
}

