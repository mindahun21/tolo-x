package com.tolox.notificationpreferenceservice.rule;

import com.tolox.notificationpreferenceservice.dto.DecisionResult;
import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
@Order(30)
public class QuietHoursRule implements Rule {
    @Override
    public DecisionResult apply(PreferenceContext context) {
        if (context.channelSettings() == null || 
            context.channelSettings().getQuietHoursStart() == null || 
            context.channelSettings().getQuietHoursEnd() == null) {
            return new DecisionResult(false, "", false);
        }

        LocalTime now = LocalTime.from(context.currentTime().atZone(java.time.ZoneId.of(
                context.channelSettings().getTimezone() != null ? context.channelSettings().getTimezone() : "UTC"
        )));
        
        LocalTime start = context.channelSettings().getQuietHoursStart();
        LocalTime end = context.channelSettings().getQuietHoursEnd();

        boolean inQuietHours;
        if (start.isBefore(end)) {
            inQuietHours = !now.isBefore(start) && now.isBefore(end);
        } else {
            // Overnights (e.g., 22:00 to 07:00)
            inQuietHours = !now.isBefore(start) || now.isBefore(end);
        }

        if (inQuietHours) {
            return new DecisionResult(false, "QUIET_HOURS", true);
        }

        return new DecisionResult(false, "", false);
    }
}
