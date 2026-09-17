package com.arbor.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 安全配置
 *
 * @author gwl
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${arbor.auth.skip:false}")
    private boolean skipAuth;

    /** CORS 允许的来源白名单（配置项 arbor.cors.allowed-origin-patterns，支持环境变量覆盖）。
     *  生产环境前后端同源部署无需 CORS；开发环境默认仅放行本机 vite 端口 */
    @Value("#{'${arbor.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}'.split(',')}")
    private List<String> corsAllowedOriginPatterns;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                if (skipAuth) {
                    auth.anyRequest().permitAll();
                } else {
                    auth
                        // 登录和认证相关接口放行
                        .requestMatchers("/api/auth/**").permitAll()
                        // 文件下载放行（window.open 无法携带 Authorization header）
                        .requestMatchers("/api/file/download/**").permitAll()
                        // OpenAPI 文档与 Swagger UI 放行（生产环境建议通过 springdoc.api-docs.enabled=false 整体关闭）
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 静态资源放行（前端 SPA）
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/icons.svg", "/assets/**").permitAll()
                        // 其他所有接口需要认证
                        .anyRequest().authenticated();
                }
            })
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** CORS 配置 — 全应用唯一 CORS 配置源（勿再新增其他 CorsFilter） */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(corsAllowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
