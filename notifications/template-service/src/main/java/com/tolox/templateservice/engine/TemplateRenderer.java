package com.tolox.templateservice.engine;

import com.tolox.templateservice.enums.TemplateEngineEnum;
import java.util.Map;
import java.util.Set;

public interface TemplateRenderer {
    String render(String content, Map<String, Object> data);
    boolean validate(String content);
    Set<String> extractVariables(String content);
    TemplateEngineEnum getEngineType();
}
