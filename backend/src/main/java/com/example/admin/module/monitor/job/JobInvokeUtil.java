package com.example.admin.module.monitor.job;

import com.example.admin.common.SpringContextHolder;

import java.lang.reflect.Method;

public final class JobInvokeUtil {

    private JobInvokeUtil() {
    }

    public static void invoke(String invokeTarget) throws Exception {
        String[] parts = invokeTarget.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invokeTarget must be beanName.method");
        }
        Object bean = SpringContextHolder.getBean(parts[0]);
        Method method = bean.getClass().getMethod(parts[1]);
        method.invoke(bean);
    }
}

