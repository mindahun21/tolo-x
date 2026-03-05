package com.tolox.templateservice.dto;

import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String applicationCode,
        String templateCode,
        String description,
        Integer activeVersionNumber,
        Instant createdAt,
        Instant updatedAt
) {}
