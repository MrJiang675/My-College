package com.argus.rag.common.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

@Configuration
public class CorsConfiguration {

    private final UrlBasedCorsConfigurationSource source;

    public CorsConfiguration(@Value("${argus.rag.cors.allowed-origins}") String[] allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins)
                .map(String::trim)
                .map(origin -> origin.replaceAll("/+$", ""))
                .toList();
        if (origins.isEmpty() || origins.stream().anyMatch(origin -> origin.isEmpty() || origin.contains("*"))) {
            throw new IllegalStateException("argus.rag.cors.allowed-origins 必须配置准确的 Origin，不允许空值或通配符");
        }

        org.springframework.web.cors.CorsConfiguration cors = new org.springframework.web.cors.CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);

        this.source = new UrlBasedCorsConfigurationSource();
        this.source.registerCorsConfiguration("/api/**", cors);
    }

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setName("argusCorsFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/api/*");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
        return registration;
    }
}
