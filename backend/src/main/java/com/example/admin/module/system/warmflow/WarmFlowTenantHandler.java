package com.example.admin.module.system.warmflow;

import com.example.admin.common.TenantContext;
import org.dromara.warm.flow.core.handler.TenantHandler;
import org.springframework.stereotype.Component;

@Component
public class WarmFlowTenantHandler implements TenantHandler {

    @Override
    public String getTenantId() {
        return String.valueOf(TenantContext.getTenantId());
    }
}
