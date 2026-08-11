package com.example.admin.module.importexport.handler;

import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 导入导出处理器注册表：注入全部 ImportExportHandler Bean，按 entityKey() 建立路由索引。
 */
@Component
@RequiredArgsConstructor
public class ImportExportHandlerRegistry {

    private final Map<String, ImportExportHandler> handlerMap;

    private final Map<String, ImportExportHandler> byEntityKey = new HashMap<>();

    @PostConstruct
    void init() {
        handlerMap.values().forEach(handler -> byEntityKey.put(handler.entityKey(), handler));
    }

    /**
     * 按目标实体标识取处理器；未注册时报 PARAM_ERROR（模板保存时即校验，处理器轮询兜底）。
     */
    public ImportExportHandler get(String entityKey) {
        ImportExportHandler handler = byEntityKey.get(entityKey);
        if (handler == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的目标实体: " + entityKey);
        }
        return handler;
    }

    public boolean supports(String entityKey) {
        return byEntityKey.containsKey(entityKey);
    }
}
