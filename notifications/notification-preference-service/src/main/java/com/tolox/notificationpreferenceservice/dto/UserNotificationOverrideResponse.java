package com.tolox.notificationpreferenceservice.dto;

import lombok.Builder;

@Builder
public record UserNotificationOverrideResponse(
        String notificationTypeCode,
        boolean enabled
) {}
