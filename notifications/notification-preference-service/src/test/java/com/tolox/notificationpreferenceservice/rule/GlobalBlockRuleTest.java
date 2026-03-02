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
 * Unit tests for {@link GlobalBlockRule}.
 * Verifies that notifications are blocked when the user has disabled the entire delivery channel.
 */
@ExtendWith(MockitoExtension.class)
class GlobalBlockRuleTest {

    @InjectMocks
    private GlobalBlockRule globalBlockRule;

    @Mock
    private PreferenceContext context;

    @Mock
    private UserChannelSettings settings;

    @Test
    @DisplayName("Should block notification when channel is explicitly blocked")
    void apply_WhenChannelIsBlocked_ShouldReturnBlockedDecision() {
        when(context.channelSettings()).thenReturn(settings);
        when(settings.isBlocked()).thenReturn(true);

        DecisionResult result = globalBlockRule.apply(context);

        assertThat(result.decisive()).isTrue();
        assertThat(result.deliver()).isFalse();
        assertThat(result.reason()).isEqualTo("CHANNEL_BLOCKED");
    }

    @Test
    @DisplayName("Should ignore rule when channel is not blocked")
    void apply_WhenChannelIsNotBlocked_ShouldReturnNonDecisive() {
        when(context.channelSettings()).thenReturn(settings);
        when(settings.isBlocked()).thenReturn(false);

        DecisionResult result = globalBlockRule.apply(context);

        assertThat(result.decisive()).isFalse();
    }

    @Test
    @DisplayName("Should handle missing channel settings gracefully")
    void apply_WhenSettingsAreNull_ShouldReturnNonDecisive() {
        when(context.channelSettings()).thenReturn(null);

        DecisionResult result = globalBlockRule.apply(context);

        assertThat(result.decisive()).isFalse();
    }
}
