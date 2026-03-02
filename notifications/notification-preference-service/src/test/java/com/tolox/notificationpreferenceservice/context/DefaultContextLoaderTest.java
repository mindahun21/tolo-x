package com.tolox.notificationpreferenceservice.context;

import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import com.tolox.notificationpreferenceservice.model.NotificationType;
import com.tolox.notificationpreferenceservice.model.UserChannelSettings;
import com.tolox.notificationpreferenceservice.model.UserNotificationOverride;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultContextLoader}.
 * Verifies the multi-level cache strategy (L1 -> L2 -> DB).
 */
@ExtendWith(MockitoExtension.class)
class DefaultContextLoaderTest {

    @Mock
    private NotificationTypeCache typeCache;
    @Mock
    private UserPreferenceCache userCache;
    @Mock
    private UserChannelSettingsRepository settingsRepository;
    @Mock
    private UserNotificationOverrideRepository overrideRepository;

    @InjectMocks
    private DefaultContextLoader contextLoader;

    @Test
    @DisplayName("Should load context from caches when data is present (All HIT)")
    void load_WhenAllCachesHit_ShouldReturnContextWithoutHittingDb() {
        // GIVEN
        String userIdStr = "1001";
        String typeCode = "LOGIN_OTP";
        String channelStr = "SMS";
        Instant now = Instant.now();

        NotificationType type = new NotificationType();
        type.setId(1L);
        type.setCode(typeCode);

        UserChannelSettings settings = new UserChannelSettings();
        UserNotificationOverride override = new UserNotificationOverride();

        when(typeCache.get(typeCode)).thenReturn(Mono.just(type));
        when(userCache.getChannel(1001L, "SMS")).thenReturn(Mono.just(settings));
        when(userCache.getOverride(1001L, 1L)).thenReturn(Mono.just(override));

        // WHEN & THEN
        StepVerifier.create(contextLoader.load(userIdStr, typeCode, channelStr, now))
                .assertNext(context -> {
                    assertThat(context.notificationType()).isEqualTo(type);
                    assertThat(context.channelSettings()).isEqualTo(settings);
                    assertThat(context.override()).isEqualTo(override);
                })
                .verifyComplete();

        // Verify DB was NOT touched
        verifyNoInteractions(settingsRepository, overrideRepository);
    }

    @Test
    @DisplayName("Should fallback to DB and populate L2 cache when cache MISS")
    void load_WhenL2CacheMiss_ShouldFetchFromDbAndPopulateCache() {
        // GIVEN
        String userIdStr = "1001";
        String typeCode = "LOGIN_OTP";
        NotificationType type = new NotificationType();
        type.setId(1L);
        
        UserChannelSettings settings = new UserChannelSettings();

        when(typeCache.get(typeCode)).thenReturn(Mono.just(type));
        
        // Settings: MISS in L2, HIT in DB
        when(userCache.getChannel(anyLong(), anyString())).thenReturn(Mono.empty());
        when(settingsRepository.findByUserIdAndChannel(anyLong(), any())).thenReturn(Mono.just(settings));
        when(userCache.putChannel(anyLong(), anyString(), any())).thenReturn(Mono.empty());

        // Override: HIT in L2
        when(userCache.getOverride(anyLong(), anyLong())).thenReturn(Mono.just(new UserNotificationOverride()));

        // WHEN & THEN
        StepVerifier.create(contextLoader.load(userIdStr, typeCode, "SMS", Instant.now()))
                .expectNextCount(1)
                .verifyComplete();

        // Verify it was saved back to cache
        verify(userCache).putChannel(eq(1001L), eq("SMS"), eq(settings));
        verify(settingsRepository).findByUserIdAndChannel(1001L, NotificationChannel.SMS);
    }

}
