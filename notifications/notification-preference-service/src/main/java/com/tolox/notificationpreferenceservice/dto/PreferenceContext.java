package com.tolox.notificationpreferenceservice.dto;

import java.time.Instant;
import com.tolox.notificationpreferenceservice.model.*;
public record PreferenceContext(
        NotificationType notificationType,
        UserChannelSettings channelSettings,
        UserNotificationOverride override,
        Instant currentTime
) {}
