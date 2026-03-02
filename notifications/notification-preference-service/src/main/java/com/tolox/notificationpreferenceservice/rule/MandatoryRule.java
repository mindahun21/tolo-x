package com.tolox.notificationpreferenceservice.rule;

import com.tolox.notificationpreferenceservice.dto.DecisionResult;
import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class MandatoryRule implements Rule {
    @Override
    public DecisionResult apply(PreferenceContext context) {
        if(context.notificationType().isMandatory()){
            return new DecisionResult(true,"MANDATORY",true);
        }
        return new DecisionResult(false,"",false);
    }
}
