package cn.admin.scaffold.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.system.entity.SysMessageDO;
import cn.admin.scaffold.module.system.mapper.SysMessageMapper;
import cn.admin.scaffold.module.system.mapper.SysMessageReadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SystemMessageService {

    private static final int MIN_LATEST_LIMIT = 1;
    private static final int MAX_LATEST_LIMIT = 20;
    private static final String CONTENT_TYPE_TEXT = "TEXT";

    private final SysMessageMapper messageMapper;
    private final SysMessageReadMapper messageReadMapper;
    private final ApplicationEventPublisher eventPublisher;

    public PageResult<SysMessageDO> page(long pageNum, long pageSize, String messageType, Integer readStatus) {
        Long userId = cn.admin.scaffold.security.SecurityUtils.getUserId();
        Page<SysMessageDO> page = new Page<>(pageNum, pageSize);
        IPage<SysMessageDO> result = messageMapper.selectMessagePage(
                page,
                TenantContext.getTenantId(),
                userId,
                StringUtils.hasText(messageType) ? messageType : null,
                readStatus);
        return PageResult.of(result, result.getRecords());
    }

    public List<SysMessageDO> latest(int limit) {
        Long userId = cn.admin.scaffold.security.SecurityUtils.getUserId();
        return messageMapper.selectLatest(
                TenantContext.getTenantId(),
                userId,
                Math.min(Math.max(limit, MIN_LATEST_LIMIT), MAX_LATEST_LIMIT));
    }

    public SysMessageDO detail(Long userId, Long id) {
        SysMessageDO message = messageMapper.selectDetail(TenantContext.getTenantId(), userId, id);
        if (message == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return message;
    }

    public long unreadCount(Long userId) {
        return messageMapper.selectUnreadCount(TenantContext.getTenantId(), userId);
    }

    public void markRead(Long userId, Long messageId) {
        Long tenantId = TenantContext.getTenantId();
        long visible = messageMapper.selectCount(new LambdaQueryWrapper<SysMessageDO>()
                .eq(SysMessageDO::getId, messageId)
                .eq(SysMessageDO::getTenantId, tenantId)
                .and(wrapper -> wrapper.eq(SysMessageDO::getReceiverId, userId)
                        .or().isNull(SysMessageDO::getReceiverId)));
        if (visible == 0) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        messageReadMapper.markRead(tenantId, messageId, userId);
    }

    public void markAllRead(Long userId) {
        messageReadMapper.markAllRead(TenantContext.getTenantId(), userId);
    }

    public Long send(Long senderId, String messageType, String title, String content,
                     String bizType, Long bizId, List<Long> receiverIds) {
        return sendWithType(senderId, messageType, title, content, CONTENT_TYPE_TEXT, bizType, bizId, receiverIds);
    }

    /** 支持富文本：contentType 为 TEXT/HTML，前端按类型渲染（HTML 不转义）。 */
    public Long sendWithType(Long senderId, String messageType, String title, String content, String contentType,
                             String bizType, Long bizId, List<Long> receiverIds) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return sendBroadcastWithType(senderId, messageType, title, content, contentType, bizType, bizId);
        }
        List<Long> distinctIds = receiverIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return sendBroadcastWithType(senderId, messageType, title, content, contentType, bizType, bizId);
        }
        Long firstId = null;
        for (Long receiverId : distinctIds) {
            Long id = insert(senderId, receiverId, messageType, title, content, contentType, bizType, bizId);
            if (firstId == null) {
                firstId = id;
            }
            push(receiverId, id, messageType, title, bizType, bizId);
        }
        return firstId;
    }

    public Long sendBroadcast(Long senderId, String messageType, String title, String content,
                              String bizType, Long bizId) {
        return sendBroadcastWithType(senderId, messageType, title, content, CONTENT_TYPE_TEXT, bizType, bizId);
    }

    public Long sendBroadcastWithType(Long senderId, String messageType, String title, String content,
                                      String contentType, String bizType, Long bizId) {
        Long id = insert(senderId, null, messageType, title, content, contentType, bizType, bizId);
        // 事务提交后推送，事务回滚则消息与推送一并撤销
        eventPublisher.publishEvent(new MessagePushEvent(null, pushPayload(id, messageType, title, bizType, bizId)));
        return id;
    }

    public void sendTodoToUsers(List<Long> userIds, Long tenantId, String title, String content,
                                String bizType, Long bizId) {
        sendToUsers(userIds, tenantId, "TODO", title, content, bizType, bizId);
    }

    public void sendSystemToUsers(List<Long> userIds, Long tenantId, String title, String content,
                                  String bizType, Long bizId) {
        sendToUsers(userIds, tenantId, "SYSTEM", title, content, bizType, bizId);
    }

    private void sendToUsers(List<Long> userIds, Long tenantId, String messageType, String title,
                             String content, String bizType, Long bizId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Long currentTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);
        try {
            send(null, messageType, title, content, bizType, bizId, userIds);
        } finally {
            TenantContext.setTenantId(currentTenant);
        }
    }

    private Long insert(Long senderId, Long receiverId, String messageType, String title, String content,
                        String contentType, String bizType, Long bizId) {
        SysMessageDO message = new SysMessageDO();
        message.setTenantId(TenantContext.getTenantId());
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setMessageType(messageType);
        message.setTitle(title);
        message.setContent(content);
        message.setContentType(contentType);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setPriority("NORMAL");
        message.setCreatedBy(senderId);
        messageMapper.insert(message);
        return message.getId();
    }

    private void push(Long userId, Long id, String messageType, String title,
                      String bizType, Long bizId) {
        eventPublisher.publishEvent(new MessagePushEvent(userId, pushPayload(id, messageType, title, bizType, bizId)));
    }

    private Map<String, Object> pushPayload(Long id, String messageType, String title,
                                            String bizType, Long bizId) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", "MESSAGE");
        payload.put("id", id);
        payload.put("messageType", messageType);
        payload.put("title", title);
        payload.put("bizType", bizType == null ? "" : bizType);
        payload.put("bizId", bizId == null ? Long.valueOf(0L) : bizId);
        payload.put("createdAt", LocalDateTime.now().toString());
        return payload;
    }
}
