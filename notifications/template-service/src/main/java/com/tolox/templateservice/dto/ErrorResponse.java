package com.tolox.templateservice.dto;

import java.time.Instant;

public record ErrorResponse(
        String message,
        String errorCode,
        Instant timestamp
) {}
