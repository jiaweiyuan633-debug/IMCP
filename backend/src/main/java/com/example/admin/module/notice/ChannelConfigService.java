package com.example.admin.module.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.LogMaskUtils;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.SecretCipher;
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
    private final ChannelConfigCipher channelConfigCipher;
    private final SecretCipher secretCipher;

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
        // R4-1.37：敏感字段落库为 enc: 密文，发送前解密为明文再交给渠道 sender（sender 不感知密文）
        SysChannelConfigDO plainConfig = channelConfigCipher.decryptConfigOf(config);

        SysChannelLogDO log = new SysChannelLogDO();
        log.setChannelType(type.name());
        log.setChannelId(config.getId());
        // R4-1.38：target/content 加密落库（PII 防护，列长已随 V62 扩列）。title 为定位信息不含正文，明文保留。
        log.setTarget(encryptSensitive(request.getTarget()));
        log.setTitle(request.getTitle());
        log.setContent(encryptSensitive(request.getContent()));
        try {
            String error = sender.send(plainConfig, request.getTarget(), request.getTitle(), request.getContent());
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
     * 解析落库的 configJson：更新场景合并打码占位（批8d），随后统一加密敏感字段（批10）。
     * 回显时敏感值被 {@link LogMaskUtils} 打码为 ******，前端若未改动直接回写会把真实密钥覆盖为
     * 掩码；此处将请求中的占位符叶子用库中原值补齐，仅"确系用户新输入"的值入库。
     * {@link ChannelConfigCipher#encryptConfig} 幂等：未改动的敏感字段（merge 补回的 enc: 密文）
     * 跳过，新输入的明文加密落库；地址字段（webhook/url 等）不加密，回显可正常编辑。新建（无 id）不合并。
     */
    private String resolveConfigJson(ChannelConfigSaveRequest request) {
        String resolved;
        if (request.getId() == null || !StringUtils.hasText(request.getConfigJson())) {
            resolved = request.getConfigJson();
        } else {
            SysChannelConfigDO existing = channelConfigMapper.selectById(request.getId());
            if (existing == null || !StringUtils.hasText(existing.getConfigJson())) {
                resolved = request.getConfigJson();
            } else {
                resolved = LogMaskUtils.mergeMasked(request.getConfigJson(), existing.getConfigJson(), objectMapper);
            }
        }
        return channelConfigCipher.encryptConfig(resolved);
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
                .target(decryptOrMask(log.getTarget()))
                .title(log.getTitle())
                .content(decryptOrMask(log.getContent()))
                .status(log.getStatus())
                .errorMsg(log.getErrorMsg())
                .createdAt(log.getCreatedAt())
                .build();
    }

    /** 落库加密：空值原样保留，非空明文用 SecretCipher 加密（"enc:" 前缀）。 */
    private String encryptSensitive(String plain) {
        return StringUtils.hasText(plain) ? secretCipher.encrypt(plain) : plain;
    }

    /**
     * 回显解密（R4-1.38）：仅解密 "enc:" 密文；解密失败（密钥变更/损坏）与存量明文
     * （V62 之前的旧数据）统一 fail-closed 打码，不回显真实内容。
     */
    private String decryptOrMask(String stored) {
        if (!StringUtils.hasText(stored)) {
            return stored;
        }
        if (secretCipher.isEncrypted(stored)) {
            String plain = secretCipher.decrypt(stored);
            return plain != null ? plain : LogMaskUtils.MASK;
        }
        return LogMaskUtils.MASK;
    }
}
