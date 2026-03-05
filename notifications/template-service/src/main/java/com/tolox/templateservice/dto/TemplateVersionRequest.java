package com.tolox.templateservice.dto;

import com.tolox.templateservice.enums.ChannelType;
import com.tolox.templateservice.enums.TemplateEngine;

public record TemplateVersionRequest(
        ChannelType channel,
        String locale,
        String subject,
        String body,
        TemplateEngine engine
) {}
