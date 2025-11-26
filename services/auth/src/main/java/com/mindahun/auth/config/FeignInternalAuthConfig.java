package com.mindahun.auth.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignInternalAuthConfig {
    @Value("${internal.token}")
    private String internalToken;

    @Bean
    public RequestInterceptor internalAuthInterceptor() {
        return requestTemplate ->
                requestTemplate.header("X-Internal-Token", internalToken);
    }
}
