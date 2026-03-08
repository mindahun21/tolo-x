package com.tolox.templateservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

@Configuration
public class TemplateEngineConfig {

    @Bean
    public TemplateEngine thymeleafEngine() {
        TemplateEngine engine = new TemplateEngine();
        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.HTML);
        // Enable caching for better performance if reusing templates
        templateResolver.setCacheable(true);
        engine.setTemplateResolver(templateResolver);
        
        // Initializing/Warming up the engine
        engine.process("Warmup", new org.thymeleaf.context.Context());
        
        return engine;
    }
}
