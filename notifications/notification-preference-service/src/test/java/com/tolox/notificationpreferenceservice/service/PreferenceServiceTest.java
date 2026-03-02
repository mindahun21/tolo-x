package com.tolox.notificationpreferenceservice.service;

import com.tolox.notificationpreferenceservice.context.ContextLoader;
import com.tolox.notificationpreferenceservice.dto.DecisionResult;
import com.tolox.notificationpreferenceservice.dto.EvaluateRequest;
import com.tolox.notificationpreferenceservice.dto.EvaluateResponse;
import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import com.tolox.notificationpreferenceservice.rule.RuleEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PreferenceService}.
 * Orchestrates the evaluation flow between the context loader and the rule engine.
 */
@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    @Mock
    private ContextLoader contextLoader;
    @Mock
    private RuleEngine ruleEngine;

    @InjectMocks
    private PreferenceService preferenceService;

    @Test
    @DisplayName("Should successfully coordinate evaluation logic")
    void evaluate_WhenValidRequest_ShouldReturnEvaluationResult() {
        // GIVEN
        EvaluateRequest request = new EvaluateRequest("1001", "OTP", "PUSH", Instant.now());
        PreferenceContext context = mock(PreferenceContext.class);
        DecisionResult result = new DecisionResult(true, "RULE_MATCHED", true);

        when(contextLoader.load(any(), any(), any(), any())).thenReturn(Mono.just(context));
        when(ruleEngine.evaluate(context)).thenReturn(result);

        // WHEN & THEN
        StepVerifier.create(preferenceService.evaluate(request))
                .assertNext(response -> {
                    assertThat(response.deliver()).isTrue();
                    assertThat(response.reason()).isEqualTo("RULE_MATCHED");
                })
                .verifyComplete();

        verify(contextLoader).load(eq("1001"), eq("OTP"), eq("PUSH"), any());
        verify(ruleEngine).evaluate(context);
    }
    
}
