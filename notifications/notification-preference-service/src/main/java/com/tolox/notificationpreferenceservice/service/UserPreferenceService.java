package com.tolox.notificationpreferenceservice.service;

import com.tolox.notificationpreferenceservice.context.UserPreferenceCache;
import com.tolox.notificationpreferenceservice.dto.UserChannelSettingsRequest;
import com.tolox.notificationpreferenceservice.dto.UserChannelSettingsResponse;
import com.tolox.notificationpreferenceservice.dto.UserNotificationOverrideRequest;
import com.tolox.notificationpreferenceservice.dto.UserNotificationOverrideResponse;
import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import com.tolox.notificationpreferenceservice.model.UserChannelSettings;
import com.tolox.notificationpreferenceservice.model.UserNotificationOverride;
import com.tolox.notificationpreferenceservice.repository.NotificationTypeRepository;
import com.tolox.notificationpreferenceservice.repository.UserChannelSettingsRepository;
import com.tolox.notificationpreferenceservice.repository.UserNotificationOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {
    private final UserChannelSettingsRepository settingsRepository;
    private final UserNotificationOverrideRepository overrideRepository;
    private final NotificationTypeRepository typeRepository;
    private final UserPreferenceCache userCache;

    public Flux<UserChannelSettingsResponse> getChannels(Long userId) {
        return settingsRepository.findAll()
                .filter(s -> s.getUserId().equals(userId))
                .map(this::mapToChannelResponse);
    }

    public Mono<Boolean> updateChannel(Long userId, NotificationChannel channel, UserChannelSettingsRequest request) {
        return settingsRepository.findByUserIdAndChannel(userId, channel)
                .defaultIfEmpty(new UserChannelSettings())
                .flatMap(s -> {
                    s.setUserId(userId);
                    s.setChannel(channel);
                    s.setBlocked(request.blocked());
                    s.setQuietHoursStart(request.quietHoursStart());
                    s.setQuietHoursEnd(request.quietHoursEnd());
                    s.setTimezone(request.timezone());
                    return settingsRepository.save(s)
                            .flatMap(saved -> userCache.evictChannel(userId, channel.name()))
                            .thenReturn(true);
                });
    }

    public Mono<Boolean> setLegalOptOut(Long userId, NotificationChannel channel, boolean value) {
        return settingsRepository.findByUserIdAndChannel(userId, channel)
                .flatMap(s -> {
                    s.setLegalOptOut(value);
                    return settingsRepository.save(s)
                            .flatMap(saved -> userCache.evictChannel(userId, channel.name()))
                            .thenReturn(true);
                })
                .defaultIfEmpty(false);
    }

    public Flux<UserNotificationOverrideResponse> getOverrides(Long userId) {
        return overrideRepository.findAll()
                .filter(o -> o.getUserId().equals(userId))
                .flatMap(o -> typeRepository.findById(o.getNotificationTypeId())
                        .map(t -> new UserNotificationOverrideResponse(t.getCode(), o.isEnabled())));
    }

    public Mono<Boolean> updateOverride(Long userId, String code, UserNotificationOverrideRequest request) {
        return typeRepository.findByCode(code)
                .flatMap(type -> overrideRepository.findByUserIdAndNotificationTypeId(userId, type.getId())
                        .defaultIfEmpty(new UserNotificationOverride())
                        .flatMap(o -> {
                            o.setUserId(userId);
                            o.setNotificationTypeId(type.getId());
                            o.setEnabled(request.enabled());
                            o.setUpdatedAt(Instant.now());
                            return overrideRepository.save(o)
                                    .flatMap(saved -> userCache.evictOverrides(userId))
                                    .thenReturn(true);
                        }));
    }

    public Mono<Boolean> deleteOverride(Long userId, String code) {
        return typeRepository.findByCode(code)
                .flatMap(type -> overrideRepository.findByUserIdAndNotificationTypeId(userId, type.getId())
                        .flatMap(o -> overrideRepository.delete(o)
                                .then(userCache.evictOverrides(userId))
                                .thenReturn(true)))
                .defaultIfEmpty(false);
    }

    private UserChannelSettingsResponse mapToChannelResponse(UserChannelSettings s) {
        return UserChannelSettingsResponse.builder()
                .channel(s.getChannel())
                .blocked(s.isBlocked())
                .legalOptOut(s.isLegalOptOut())
                .quietHoursStart(s.getQuietHoursStart())
                .quietHoursEnd(s.getQuietHoursEnd())
                .timezone(s.getTimezone())
                .build();
    }
}
