package cn.admin.scaffold.common;

import cn.admin.scaffold.common.annotation.DataScope;
import cn.admin.scaffold.module.system.DataScopeHelper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final DataScopeHelper dataScopeHelper;

    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        DataScopeContext.clear();
        if (!dataScopeHelper.isAdmin()) {
            List<Long> userIds = dataScopeHelper.allowedUserIds();
            List<String> usernames = dataScopeHelper.allowedUsernames();
            DataScopeContext.set(new DataScopeContext.Filter(
                    userIds,
                    usernames,
                    Set.of(dataScope.tables()),
                    userIds != null && userIds.isEmpty()));
        }
        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }
}
