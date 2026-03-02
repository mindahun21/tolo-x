package com.tolox.notificationpreferenceservice.dto;

import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import lombok.Builder;
import java.time.LocalTime;

@Builder
public record UserChannelSettingsResponse(
        NotificationChannel channel,
        boolean blocked,
        boolean legalOptOut,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,
        String timezone
) {}
