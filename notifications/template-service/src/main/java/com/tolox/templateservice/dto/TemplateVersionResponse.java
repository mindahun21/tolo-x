package com.tolox.templateservice.dto;

import com.tolox.templateservice.enums.ChannelType;
import com.tolox.templateservice.enums.TemplateEngineEnum;
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
        TemplateEngineEnum engine,
        VersionStatus status,
        Instant createdAt
) {}
