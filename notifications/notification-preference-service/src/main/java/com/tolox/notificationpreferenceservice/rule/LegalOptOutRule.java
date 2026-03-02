package com.tolox.notificationpreferenceservice.rule;

import com.tolox.notificationpreferenceservice.dto.DecisionResult;
import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(25)
public class LegalOptOutRule implements Rule {
    @Override
    public DecisionResult apply(PreferenceContext context) {
        if (context.channelSettings() == null) {
            return new DecisionResult(false, "", false);
        }
        if (context.channelSettings().isLegalOptOut()) {
            return new DecisionResult(false, "LEGAL_OPT_OUT", true);
        }
        return new DecisionResult(false, "", false);
    }
}
