package com.tolox.notificationpreferenceservice.context;

import com.tolox.notificationpreferenceservice.model.UserChannelSettings;
import com.tolox.notificationpreferenceservice.model.UserNotificationOverride;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPreferenceCache {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private static final String CHANNEL_KEY_PREFIX = "user:channel:";
    private static final String OVERRIDE_KEY_PREFIX = "user:overrides:";
    private static final Duration TTL = Duration.ofHours(24);

    public Mono<UserChannelSettings> getChannel(Long userId, String channel) {
        return redisTemplate.opsForValue()
                .get(CHANNEL_KEY_PREFIX + userId + ":" + channel)
                .cast(UserChannelSettings.class);
    }

    public Mono<Void> putChannel(Long userId, String channel, UserChannelSettings settings) {
        return redisTemplate.opsForValue()
                .set(CHANNEL_KEY_PREFIX + userId + ":" + channel, settings, TTL)
                .then();
    }

    public Mono<UserNotificationOverride> getOverride(Long userId, Long typeId) {
        return redisTemplate.opsForHash()
                .get(OVERRIDE_KEY_PREFIX + userId, String.valueOf(typeId))
                .cast(UserNotificationOverride.class);
    }

    public Mono<Void> putOverride(Long userId, Long typeId, UserNotificationOverride override) {
        return redisTemplate.opsForHash()
                .put(OVERRIDE_KEY_PREFIX + userId, String.valueOf(typeId), override)
                .then(redisTemplate.expire(OVERRIDE_KEY_PREFIX + userId, TTL))
                .then();
    }

    public Mono<Void> evict(Long userId) {
        return redisTemplate.delete(CHANNEL_KEY_PREFIX + userId + ":*") // Redis delete multiple keys with pattern is tricky in reactive, let's just delete the specific ones or use a key strategy.
                .then(redisTemplate.delete(OVERRIDE_KEY_PREFIX + userId))
                .then();
    }

    public Mono<Void> evictChannel(Long userId, String channel) {
        return redisTemplate.delete(CHANNEL_KEY_PREFIX + userId + ":" + channel).then();
    }

    public Mono<Void> evictOverrides(Long userId) {
        return redisTemplate.delete(OVERRIDE_KEY_PREFIX + userId).then();
    }
}
