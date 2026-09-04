package cn.admin.scaffold.module.system.warmflow;

import cn.admin.scaffold.common.TenantContext;
import org.dromara.warm.flow.core.handler.TenantHandler;
import org.springframework.stereotype.Component;

@Component
public class WarmFlowTenantHandler implements TenantHandler {

    @Override
    public String getTenantId() {
        return String.valueOf(TenantContext.getTenantId());
    }
}
