package com.example.admin.module.ai.manager;

import com.example.admin.module.ai.AiPythonClient;
import com.example.admin.module.ai.entity.AiServiceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiTaskManager {

    private final AiPythonClient pythonClient;

    public Map<String, Object> submit(
            AiServiceConfig config,
            String taskNo,
            String bizType,
            Map<String, Object> params,
            String callbackUrl) {
        return pythonClient.createTask(config, taskNo, bizType, params, callbackUrl);
    }
}
