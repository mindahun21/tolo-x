package com.tolox.templateservice.dto;

import java.time.Instant;

public record RenderResponse(
        String subject,
        String body,
        Integer versionNumber,
        String locale,
        Instant renderedAt
) {}
