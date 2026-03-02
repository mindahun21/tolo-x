package com.tolox.notificationpreferenceservice.rule;

import com.tolox.notificationpreferenceservice.dto.DecisionResult;
import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class GlobalBlockRule implements Rule {
    @Override
    public DecisionResult apply(PreferenceContext context) {
        if (context.channelSettings() == null) {
            return new DecisionResult(false, "", false);
        }
        if (context.channelSettings().isBlocked()) {
            return new DecisionResult(false, "CHANNEL_BLOCKED", true);
        }
        return new DecisionResult(false, "", false);
    }
}
