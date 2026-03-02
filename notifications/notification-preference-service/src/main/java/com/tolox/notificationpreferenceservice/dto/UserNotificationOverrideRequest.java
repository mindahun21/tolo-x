package com.tolox.notificationpreferenceservice.dto;

import lombok.Builder;

@Builder
public record UserNotificationOverrideRequest(
        boolean enabled
) {}
