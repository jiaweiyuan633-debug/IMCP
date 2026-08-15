package com.example.admin.module.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.LogMaskUtils;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.notice.channel.ChannelFactory;
import com.example.admin.module.notice.channel.MessageChannelSender;
import com.example.admin.module.notice.dto.ChannelConfigQuery;
import com.example.admin.module.notice.dto.ChannelConfigSaveRequest;
import com.example.admin.module.notice.dto.ChannelSendRequest;
import com.example.admin.module.notice.entity.SysChannelConfigDO;
import com.example.admin.module.notice.entity.SysChannelLogDO;
import com.example.admin.module.notice.mapper.SysChannelConfigMapper;
import com.example.admin.module.notice.mapper.SysChannelLogMapper;
import com.example.admin.module.notice.vo.ChannelConfigVo;
import com.example.admin.module.notice.vo.ChannelLogVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息渠道服务：渠道配置 CRUD + 多渠道发送（邮件/短信/钉钉/企微）并记录发送日志。
 */
@Service
@RequiredArgsConstructor
public class ChannelConfigService {

    private static final int ENABLED = 1;
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILURE = 0;

    private final SysChannelConfigMapper channelConfigMapper;
    private final SysChannelLogMapper channelLogMapper;
    private final ChannelFactory channelFactory;
    private final ObjectMapper objectMapper;

    public PageResult<ChannelConfigVo> page(ChannelConfigQuery query) {
        Page<SysChannelConfigDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysChannelConfigDO> wrapper = new LambdaQueryWrapper<SysChannelConfigDO>()
                .eq(StringUtils.hasText(query.getChannelType()), SysChannelConfigDO::getChannelType, query.getChannelType())
                .eq(query.getStatus() != null, SysChannelConfigDO::getStatus, query.getStatus())
                .orderByAsc(SysChannelConfigDO::getSort)
                .orderByAsc(SysChannelConfigDO::getId);
        IPage<SysChannelConfigDO> result = channelConfigMapper.selectPage(page, wrapper);
        List<ChannelConfigVo> records = result.getRecords().stream().map(c -> toVo(c)).toList();
        return PageResult.of(result, records);
    }

    public Long create(ChannelConfigSaveRequest request) {
        SysChannelConfigDO config = toEntity(request);
        channelConfigMapper.insert(config);
        return config.getId();
    }

    public void update(ChannelConfigSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "渠道配置 ID 不能为空");
        }
        channelConfigMapper.updateById(toEntity(request));
    }

    public void updateStatus(Long id, Integer status) {
        SysChannelConfigDO config = new SysChannelConfigDO();
        config.setId(id);
        config.setStatus(status);
        channelConfigMapper.updateById(config);
    }

    public void delete(Long id) {
        channelConfigMapper.deleteById(id);
    }

    /** 通过指定渠道发送消息，发送结果写入发送记录（失败不阻断主流程，仅记录）。 */
    public Long send(ChannelSendRequest request) {
        SysChannelConfigDO config = channelConfigMapper.selectById(request.getChannelId());
        if (config == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        if (config.getStatus() == null || config.getStatus() != ENABLED) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "渠道已停用");
        }
        ChannelType type = parseType(config.getChannelType());
        MessageChannelSender sender = channelFactory.get(type);

        SysChannelLogDO log = new SysChannelLogDO();
        log.setChannelType(type.name());
        log.setChannelId(config.getId());
        log.setTarget(request.getTarget());
        log.setTitle(request.getTitle());
        log.setContent(request.getContent());
        try {
            String error = sender.send(config, request.getTarget(), request.getTitle(), request.getContent());
            log.setStatus(error == null ? STATUS_SUCCESS : STATUS_FAILURE);
            log.setErrorMsg(error);
        } catch (Exception e) {
            log.setStatus(STATUS_FAILURE);
            log.setErrorMsg(e.getMessage());
        }
        log.setCreatedAt(LocalDateTime.now());
        channelLogMapper.insert(log);
        return log.getId();
    }

    /**
     * 带重试的渠道发送：{@code sender.send} 以返回非 null 字符串表示失败（不抛异常），
     * 本方法将失败包装为 {@link ChannelSendException} 触发 spring-retry 重试（1s 退避，最多 3 次）。
     * 业务参数错误（渠道不存在/停用）抛 {@link BusinessException}，不匹配 retryFor，不重试。
     * 每次尝试写一条发送日志，重试失败的最终错误由最后一次日志承载。
     */
    @Retryable(retryFor = ChannelSendException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public Long sendWithRetry(ChannelSendRequest request) {
        Long logId = send(request);
        SysChannelLogDO log = channelLogMapper.selectById(logId);
        if (log == null || log.getStatus() == null || log.getStatus() != STATUS_SUCCESS) {
            throw new ChannelSendException(log == null ? "发送日志不存在" : log.getErrorMsg());
        }
        return logId;
    }

    public PageResult<ChannelLogVo> logPage(ChannelConfigQuery query) {
        Page<SysChannelLogDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysChannelLogDO> wrapper = new LambdaQueryWrapper<SysChannelLogDO>()
                .eq(StringUtils.hasText(query.getChannelType()), SysChannelLogDO::getChannelType, query.getChannelType())
                .eq(query.getStatus() != null, SysChannelLogDO::getStatus, query.getStatus())
                .orderByDesc(SysChannelLogDO::getId);
        IPage<SysChannelLogDO> result = channelLogMapper.selectPage(page, wrapper);
        List<ChannelLogVo> records = result.getRecords().stream().map(l -> toLogVo(l)).toList();
        return PageResult.of(result, records);
    }

    private ChannelType parseType(String channelType) {
        try {
            return ChannelType.valueOf(channelType);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "非法渠道类型: " + channelType);
        }
    }

    private SysChannelConfigDO toEntity(ChannelConfigSaveRequest request) {
        SysChannelConfigDO config = new SysChannelConfigDO();
        config.setId(request.getId());
        config.setChannelType(request.getChannelType());
        config.setChannelName(request.getChannelName());
        config.setConfigJson(resolveConfigJson(request));
        config.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        config.setSort(request.getSort() == null ? 0 : request.getSort());
        config.setDescription(request.getDescription());
        return config;
    }

    /**
     * 解析落库的 configJson：更新场景合并打码占位（批8d）。回显时敏感值被 {@link LogMaskUtils}
     * 打码为 ******，前端若未改动直接回写会把真实密钥覆盖为掩码；此处将请求中的占位符叶子
     * 用库中原值补齐，仅"确系用户新输入"的值入库。新建（无 id）不合并。
     */
    private String resolveConfigJson(ChannelConfigSaveRequest request) {
        if (request.getId() == null || !StringUtils.hasText(request.getConfigJson())) {
            return request.getConfigJson();
        }
        SysChannelConfigDO existing = channelConfigMapper.selectById(request.getId());
        if (existing == null || !StringUtils.hasText(existing.getConfigJson())) {
            return request.getConfigJson();
        }
        return LogMaskUtils.mergeMasked(request.getConfigJson(), existing.getConfigJson(), objectMapper);
    }

    private ChannelConfigVo toVo(SysChannelConfigDO config) {
        return ChannelConfigVo.builder()
                .id(config.getId())
                .channelType(config.getChannelType())
                .channelName(config.getChannelName())
                .configJson(LogMaskUtils.maskStructuredConfig(config.getConfigJson(), objectMapper))
                .status(config.getStatus())
                .sort(config.getSort())
                .description(config.getDescription())
                .createdAt(config.getCreatedAt())
                .build();
    }

    private ChannelLogVo toLogVo(SysChannelLogDO log) {
        return ChannelLogVo.builder()
                .id(log.getId())
                .channelType(log.getChannelType())
                .channelId(log.getChannelId())
                .target(log.getTarget())
                .title(log.getTitle())
                .content(log.getContent())
                .status(log.getStatus())
                .errorMsg(log.getErrorMsg())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
