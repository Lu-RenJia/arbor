package com.arbor.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置 — 访问 /swagger-ui/index.html 在线调试。
 * <p>
 * 生产环境通过 springdoc.api-docs.enabled=false 关闭（见 application.yml）。
 * </p>
 *
 * @author gwl
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI arborOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Arbor API")
                        .description("Arbor 数据平台接口文档 — 基于自研轻量流程引擎")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录后将返回的 token 填入（无需 Bearer 前缀）")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
