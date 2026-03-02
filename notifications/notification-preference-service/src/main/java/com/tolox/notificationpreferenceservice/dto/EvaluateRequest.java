package com.tolox.notificationpreferenceservice.dto;

import java.time.Instant;

public record EvaluateRequest(
        String userId,
        String notificationTypeCode,
        String channel,
        Instant currentTime
) {}
