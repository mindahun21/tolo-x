package com.tolox.notificationpreferenceservice.context;

import com.tolox.notificationpreferenceservice.dto.PreferenceContext;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface ContextLoader {
    Mono<PreferenceContext> load(
            String userId,
            String notificationTypeCode,
            String channel,
            Instant currentTime
    );
}
