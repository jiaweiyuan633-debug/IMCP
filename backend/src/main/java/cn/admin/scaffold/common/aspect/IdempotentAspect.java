package cn.admin.scaffold.common.aspect;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.common.annotation.Idempotent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * {@link Idempotent} 切面：基于 Redis SETNX 实现接口幂等。
 *
 * <p>键结构 {@code idem:{tenantId}:{Class.method}:{key}}。首次请求 SETNX 成功后执行业务：
 * 执行失败立即释放键（允许重试），成功且 {@code returnCached=true} 时把结果写回键（窗口期内重复请求直接返回），
 * 否则删除键。重复请求在窗口期内被拦截，避免前端重试/双击造成的重复提交。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final String KEY_PREFIX = "idem:";
    /** 占位值：键已存在但尚未写入结果（returnCached 语义下的首次执行中）。 */
    private static final String PENDING = "P";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new StandardReflectionParameterNameDiscoverer();

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String key = buildKey(signature, pjp.getArgs(), idempotent);
        Duration ttl = Duration.ofSeconds(idempotent.expireSeconds());

        Boolean first = redisTemplate.opsForValue().setIfAbsent(key, PENDING, ttl);
        if (!Boolean.TRUE.equals(first)) {
            if (idempotent.returnCached()) {
                String cached = redisTemplate.opsForValue().get(key);
                if (cached != null && !cached.isEmpty() && !PENDING.equals(cached)) {
                    Class<?> returnType = signature.getReturnType();
                    return objectMapper.readValue(cached, returnType);
                }
            }
            throw new BusinessException(ResultCode.REPEAT_SUBMIT);
        }
        try {
            Object result = pjp.proceed();
            if (idempotent.returnCached() && result != null) {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), ttl);
            } else {
                // 成功即释放：窗口期语义是"防重复提交"，不阻塞后续正常提交
                redisTemplate.delete(key);
            }
            return result;
        } catch (Throwable throwable) {
            // 失败释放键，允许客户端重试
            redisTemplate.delete(key);
            throw throwable;
        }
    }

    private String buildKey(MethodSignature signature, Object[] args, Idempotent idempotent) {
        StringBuilder sb = new StringBuilder(KEY_PREFIX);
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            sb.append(tenantId).append(':');
        }
        sb.append(signature.getDeclaringType().getSimpleName()).append('.').append(signature.getMethod().getName());
        if (StringUtils.hasText(idempotent.key())) {
            StandardEvaluationContext context = new StandardEvaluationContext();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());
            for (int i = 0; paramNames != null && i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
            Object value;
            try {
                value = parser.parseExpression(idempotent.key()).getValue(context);
            } catch (RuntimeException exception) {
                log.warn("Idempotent key SpEL 解析失败，回退参数 JSON：{}", idempotent.key(), exception);
                value = null;
            }
            sb.append(':').append(String.valueOf(value));
        } else {
            try {
                sb.append(':').append(objectMapper.writeValueAsString(args));
            } catch (Exception exception) {
                sb.append(':').append(args.length);
            }
        }
        return sb.toString();
    }
}
