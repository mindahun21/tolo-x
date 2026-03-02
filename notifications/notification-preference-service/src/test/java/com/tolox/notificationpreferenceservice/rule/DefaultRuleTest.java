package com.tolox.notificationpreferenceservice.rule;

import com.tolox.notificationpreferenceservice.dto.DecisionResult;
import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import com.tolox.notificationpreferenceservice.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultRule}.
 * Verifies final fallback behavior based on notification registry defaults.
 */
@ExtendWith(MockitoExtension.class)
class DefaultRuleTest {

    @InjectMocks
    private DefaultRule defaultRule;

    @Mock
    private PreferenceContext context;

    @Mock
    private NotificationType notificationType;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Should use registry default setting as the final decision")
    void apply_Always_ShouldReturnRegistryDefault(boolean isEnabled) {
        when(context.notificationType()).thenReturn(notificationType);
        when(notificationType.isDefaultEnabled()).thenReturn(isEnabled);

        DecisionResult result = defaultRule.apply(context);

        assertThat(result.decisive()).isTrue();
        assertThat(result.deliver()).isEqualTo(isEnabled);
        assertThat(result.reason()).isEqualTo("DEFAULT");
    }
}
