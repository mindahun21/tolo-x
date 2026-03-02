package com.tolox.notificationpreferenceservice.dto;

import com.tolox.notificationpreferenceservice.enums.NotificationCategory;
import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import com.tolox.notificationpreferenceservice.enums.NotificationStatus;
import lombok.Builder;

import java.time.Instant;

@Builder
public record NotificationTypeRequest(
        String code,
        String appId,
        NotificationChannel channel,
        NotificationCategory category,
        boolean defaultEnabled,
        boolean mandatory,
        Integer maxFrequencyPerDay,
        Integer cooldownSeconds,
        NotificationStatus status
) {}
