package com.tolox.templateservice.dto;

import com.tolox.templateservice.enums.ChannelType;
import com.tolox.templateservice.enums.TemplateEngine;
import com.tolox.templateservice.enums.VersionStatus;

import java.time.Instant;
import java.util.UUID;

public record TemplateVersionResponse(
        UUID id,
        UUID templateId,
        Integer versionNumber,
        ChannelType channel,
        String locale,
        String subject,
        String body,
        TemplateEngine engine,
        VersionStatus status,
        Instant createdAt
) {}
