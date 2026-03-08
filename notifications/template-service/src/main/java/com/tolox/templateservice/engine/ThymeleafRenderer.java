package com.tolox.templateservice.engine;

import com.tolox.templateservice.enums.TemplateEngineEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThymeleafRenderer implements TemplateRenderer {

    private final TemplateEngine thymeleafEngine;
    
    private static final Pattern THYMELEAF_VARIABLE_PATTERN = 
            Pattern.compile("\\[\\[\\s*\\$\\{([a-zA-Z0-9._-]+)\\}\\s*\\]\\]");

    @Override
    public String render(String content, Map<String, Object> data) {
        if (content == null) return null;
        Context context = new Context();
        context.setVariables(data);
        return thymeleafEngine.process(content, context);
    }

    /**
     * Validates Thymeleaf syntax by performing a dry-run.
     * To prevent crashes on missing data (like [[${assets.LOGO}]]), 
     * we pre-scan the template for variables and inject "Safe Mocks" into the context.
     */
    @Override
    public boolean validate(String content) {
        if (content == null || content.isBlank()) return true;
        try {
            // 1. Extract all variable names used in the template (e.g. "assets.LOGO", "user")
            Set<String> variables = extractVariables(content);
            
            // 2. Create a "Safe Mock" that returns itself on any property access
            // This allows [[${assets.LOGO.url.length()}]] to pass without NPE.
            final Map<String, Object> safeMock = new HashMap<>() {
                @Override
                public Object get(Object key) {
                    return this;
                }
                @Override
                public String toString() {
                    return "MOCK_VALUE";
                }
            };

            // 3. Populate a context with mocks for every root variable found
            Context context = new Context();
            for (String var : variables) {
                // If it's "assets.LOGO", we just need to provide "assets" as a root object
                String rootVar = var.contains(".") ? var.split("\\.")[0] : var;
                context.setVariable(rootVar, safeMock);
            }

            // 4. Perform the dry-run
            thymeleafEngine.process(content, context);
            return true;
        } catch (TemplateProcessingException e) {
            // If it still fails, it's a genuine syntax error (e.g. mismatched brackets)
            log.warn("Thymeleaf actual syntax error: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during validation: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Set<String> extractVariables(String content) {
        Set<String> variables = new HashSet<>();
        if (content == null || content.isBlank()) {
            return variables;
        }

        Matcher matcher = THYMELEAF_VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        
        return variables;
    }

    @Override
    public TemplateEngineEnum getEngineType() {
        return TemplateEngineEnum.THYMELEAF;
    }
}
