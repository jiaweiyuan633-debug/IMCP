package cn.admin.scaffold.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI adminOpenApi() {
        return new OpenAPI().info(new Info()
                .title("后台管理系统 API")
                .description("Spring Boot 后端接口文档")
                .version("0.1.0"));
    }
}

