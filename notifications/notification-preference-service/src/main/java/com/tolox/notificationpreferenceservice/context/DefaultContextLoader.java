package com.tolox.notificationpreferenceservice.context;

import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import com.tolox.notificationpreferenceservice.model.NotificationType;
import com.tolox.notificationpreferenceservice.model.UserChannelSettings;
import com.tolox.notificationpreferenceservice.model.UserNotificationOverride;
import com.tolox.notificationpreferenceservice.repository.NotificationTypeRepository;
import com.tolox.notificationpreferenceservice.repository.UserChannelSettingsRepository;
import com.tolox.notificationpreferenceservice.repository.UserNotificationOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultContextLoader implements ContextLoader {
    private final NotificationTypeCache typeCache;
    private final UserPreferenceCache userCache;
    
    private final NotificationTypeRepository notificationTypeRepository;
    private final UserChannelSettingsRepository userChannelSettingsRepository;
    private final UserNotificationOverrideRepository userNotificationOverrideRepository;

    @Override
    public Mono<PreferenceContext> load(String userIdStr, String notificationTypeCode, String channelStr, Instant currentTime) {
        Long userId = Long.parseLong(userIdStr);
        NotificationChannel channel = NotificationChannel.valueOf(channelStr.toUpperCase());

        log.debug("Loading Preference Context [User: {}, Type: {}, Channel: {}]", userId, notificationTypeCode, channel);

        // L1 Cache Registry Fetch
        return typeCache.get(notificationTypeCode)
                .doOnNext(nt -> log.debug("L1 Cache HIT: Notification Registry for {}", notificationTypeCode))
                .flatMap(nt -> {
                    // L2 Cache Settings Fetch
                    Mono<Optional<UserChannelSettings>> settingsMono = userCache.getChannel(userId, channel.name())
                            .doOnNext(s -> log.info("L2 Cache HIT: Channel Settings for User: {}, Channel: {}", userId, channel))
                            .map(Optional::of)
                            .switchIfEmpty(Mono.defer(() -> userChannelSettingsRepository.findByUserIdAndChannel(userId, channel)
                                    .doOnNext(s -> log.info("L2 Cache MISS: Fetched from DB: Channel Settings for User: {}, Channel: {}", userId, channel))
                                    .flatMap(s -> userCache.putChannel(userId, channel.name(), s).thenReturn(s))
                                    .map(Optional::of)
                                    .defaultIfEmpty(Optional.empty())
                                    .doOnTerminate(() -> log.debug("L2 Cache populated for user channel settings"))));

                    // L2 Cache Overrides Fetch
                    Mono<Optional<UserNotificationOverride>> overrideMono = userCache.getOverride(userId, nt.getId())
                            .doOnNext(o -> log.info("L2 Cache HIT: Notification Override for User: {}, TypeId: {}", userId, nt.getId()))
                            .map(Optional::of)
                            .switchIfEmpty(Mono.defer(() -> userNotificationOverrideRepository.findByUserIdAndNotificationTypeId(userId, nt.getId())
                                    .doOnNext(o -> log.info("L2 Cache MISS: Fetched from DB: Notification Override for User: {}, TypeId: {}", userId, nt.getId()))
                                    .flatMap(o -> userCache.putOverride(userId, nt.getId(), o).thenReturn(o))
                                    .map(Optional::of)
                                    .defaultIfEmpty(Optional.empty())
                                    .doOnTerminate(() -> log.debug("L2 Cache populated for user overrides"))));

                    return Mono.zip(settingsMono, overrideMono)
                            .map(tuple -> new PreferenceContext(
                                    nt,
                                    tuple.getT1().orElse(null),
                                    tuple.getT2().orElse(null),
                                    currentTime
                            ));
                });
    }
}
