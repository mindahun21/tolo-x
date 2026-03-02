package com.tolox.notificationpreferenceservice.rule;

import com.tolox.notificationpreferenceservice.dto.DecisionResult;
import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RuleEngine}.
 * Verifies the sequential execution of rules and early-exit on decisiveness.
 */
@ExtendWith(MockitoExtension.class)
class RuleEngineTest {

    @Mock
    private Rule rule1;
    @Mock
    private Rule rule2;
    @Mock
    private PreferenceContext context;

    @Test
    @DisplayName("Should return the first decisive rule result and STOP")
    void evaluate_WhenFirstRuleIsDecisive_ShouldStopAndReturn() {
        // GIVEN
        DecisionResult decisiveResult = new DecisionResult(true, "R1", true);
        when(rule1.apply(any())).thenReturn(decisiveResult);

        // We use a real list for injection
        RuleEngine engine = new RuleEngine(List.of(rule1, rule2));

        // WHEN
        DecisionResult result = engine.evaluate(context);

        // THEN
        assertThat(result).isEqualTo(decisiveResult);
        verify(rule1).apply(context);
        verifyNoInteractions(rule2); // STOPS here
    }

    @Test
    @DisplayName("Should skip non-decisive rules until a decisive one is found")
    void evaluate_ShouldSkipNonDecisiveRules() {
        // GIVEN
        when(rule1.apply(any())).thenReturn(new DecisionResult(false, "IGNORE", false));
        DecisionResult decisiveResult = new DecisionResult(true, "R2", true);
        when(rule2.apply(any())).thenReturn(decisiveResult);

        RuleEngine engine = new RuleEngine(List.of(rule1, rule2));

        // WHEN
        DecisionResult result = engine.evaluate(context);

        // THEN
        assertThat(result).isEqualTo(decisiveResult);
        verify(rule1).apply(context);
        verify(rule2).apply(context);
    }
}
