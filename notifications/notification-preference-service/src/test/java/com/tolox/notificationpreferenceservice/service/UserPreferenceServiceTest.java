package com.tolox.notificationpreferenceservice.service;

import com.tolox.notificationpreferenceservice.context.UserPreferenceCache;
import com.tolox.notificationpreferenceservice.dto.UserChannelSettingsRequest;
import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import com.tolox.notificationpreferenceservice.model.UserChannelSettings;
import com.tolox.notificationpreferenceservice.repository.NotificationTypeRepository;
import com.tolox.notificationpreferenceservice.repository.UserChannelSettingsRepository;
import com.tolox.notificationpreferenceservice.repository.UserNotificationOverrideRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserPreferenceService}.
 * Focuses on business logic validation and cache consistency.
 */
@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    @Mock
    private UserChannelSettingsRepository settingsRepository;
    @Mock
    private UserNotificationOverrideRepository overrideRepository;
    @Mock
    private NotificationTypeRepository typeRepository;
    @Mock
    private UserPreferenceCache userCache;

    @InjectMocks
    private UserPreferenceService userPreferenceService;

    @Test
    @DisplayName("Should successfully update channel and evict cache")
    void updateChannel_WhenValid_ShouldSaveAndEvictCache() {
        Long userId = 1001L;
        NotificationChannel channel = NotificationChannel.SMS;
        UserChannelSettingsRequest request = new UserChannelSettingsRequest(
                true, null, null, "UTC"
        );

        UserChannelSettings settings = new UserChannelSettings();
        settings.setUserId(userId);
        settings.setChannel(channel);

        when(settingsRepository.findByUserIdAndChannel(userId, channel))
                .thenReturn(Mono.just(settings));
        when(settingsRepository.save(any(UserChannelSettings.class)))
                .thenReturn(Mono.just(settings));
        when(userCache.evictChannel(userId, channel.name()))
                .thenReturn(Mono.empty());

        StepVerifier.create(userPreferenceService.updateChannel(userId, channel, request))
                .expectNext(true)
                .verifyComplete();

        verify(userCache, times(1)).evictChannel(userId, channel.name());
        verify(settingsRepository, times(1)).save(any(UserChannelSettings.class));
    }

    @Test
    @DisplayName("Should return error and skip eviction if database save fails")
    void updateChannel_WhenDbFails_ShouldPropagateErrorAndSkipEviction() {
        Long userId = 1001L;
        NotificationChannel channel = NotificationChannel.SMS;
        UserChannelSettingsRequest request = new UserChannelSettingsRequest(true, null, null, "UTC");

        when(settingsRepository.findByUserIdAndChannel(anyLong(), any()))
                .thenReturn(Mono.just(new UserChannelSettings()));
        when(settingsRepository.save(any()))
                .thenReturn(Mono.error(new RuntimeException("DB_FAIL")));

        StepVerifier.create(userPreferenceService.updateChannel(userId, channel, request))
                .expectError(RuntimeException.class)
                .verify();

        verify(userCache, never()).evictChannel(anyLong(), anyString());
    }
}
