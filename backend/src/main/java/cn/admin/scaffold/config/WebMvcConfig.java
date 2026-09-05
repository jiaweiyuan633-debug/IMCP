package cn.admin.scaffold.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload-path:uploads}")
    private String uploadPath;

    private final cn.admin.scaffold.common.ApiRateLimitInterceptor apiRateLimitInterceptor;

    public WebMvcConfig(cn.admin.scaffold.common.ApiRateLimitInterceptor apiRateLimitInterceptor) {
        this.apiRateLimitInterceptor = apiRateLimitInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Paths.get(uploadPath).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiRateLimitInterceptor).addPathPatterns("/api/**");
    }
}

