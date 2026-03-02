package com.tolox.notificationpreferenceservice.rule;

import com.tolox.notificationpreferenceservice.dto.DecisionResult;
import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class UserOverrideRule implements Rule {
    @Override
    public DecisionResult apply(PreferenceContext context) {
        if (context.override() != null) {
            return new DecisionResult(context.override().isEnabled(), "USER_OVERRIDE", true);
        }
        return new DecisionResult(false, "", false);
    }
}
