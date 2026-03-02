package com.tolox.notificationpreferenceservice.dto;

import lombok.Builder;
import java.time.LocalTime;

@Builder
public record UserChannelSettingsRequest(
        boolean blocked,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,
        String timezone
) {}
