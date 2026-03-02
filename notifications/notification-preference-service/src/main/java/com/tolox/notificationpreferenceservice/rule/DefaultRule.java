package com.tolox.notificationpreferenceservice.rule;

import com.tolox.notificationpreferenceservice.dto.DecisionResult;
import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class DefaultRule implements Rule {

    @Override
    public DecisionResult apply(PreferenceContext context) {
        boolean deliver = context.notificationType().isDefaultEnabled();
        return new DecisionResult(deliver, "DEFAULT", true);
    }
}
