package com.tolox.notificationpreferenceservice.dto;

import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import lombok.Builder;

@Builder
public record EffectiveContextResponse(
        NotificationChannel effectiveChannel,
        boolean allowed
) {}
