package com.tolox.notificationpreferenceservice.rule;

import com.tolox.notificationpreferenceservice.dto.DecisionResult;
import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import com.tolox.notificationpreferenceservice.model.UserChannelSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LegalOptOutRule}.
 * Ensures legal mandates are respected (e.g. STOP SMS).
 */
@ExtendWith(MockitoExtension.class)
class LegalOptOutRuleTest {

    @InjectMocks
    private LegalOptOutRule legalOptOutRule;

    @Mock
    private PreferenceContext context;

    @Mock
    private UserChannelSettings settings;

    @Test
    @DisplayName("Should block notification when user has legally opted out")
    void apply_WhenLegalOptOutIsTrue_ShouldReturnBlockedDecision() {
        when(context.channelSettings()).thenReturn(settings);
        when(settings.isLegalOptOut()).thenReturn(true);

        DecisionResult result = legalOptOutRule.apply(context);

        assertThat(result.decisive()).isTrue();
        assertThat(result.deliver()).isFalse();
        assertThat(result.reason()).isEqualTo("LEGAL_OPT_OUT");
    }

    @Test
    @DisplayName("Should be non-decisive when legal opt-out is false")
    void apply_WhenLegalOptOutIsFalse_ShouldReturnNonDecisive() {
        when(context.channelSettings()).thenReturn(settings);
        when(settings.isLegalOptOut()).thenReturn(false);

        DecisionResult result = legalOptOutRule.apply(context);

        assertThat(result.decisive()).isFalse();
    }

    @Test
    @DisplayName("Should handle null settings gracefully")
    void apply_WhenSettingsAreNull_ShouldReturnNonDecisive() {
        when(context.channelSettings()).thenReturn(null);

        DecisionResult result = legalOptOutRule.apply(context);

        assertThat(result.decisive()).isFalse();
    }
}
