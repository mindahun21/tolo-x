package com.tolox.notificationpreferenceservice.rule;

import com.tolox.notificationpreferenceservice.dto.DecisionResult;
import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RuleEngine {
    private final List<Rule> rules;

    public DecisionResult evaluate(PreferenceContext context) {
        for (Rule rule : rules) {
            DecisionResult result = rule.apply(context);
            if (result.decisive()) {
                return result;
            }
        }
        return new DecisionResult(false, "NO_RULE_MATCHED", true);
    }
}
