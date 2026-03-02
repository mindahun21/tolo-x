package com.tolox.notificationpreferenceservice.repository;

import com.tolox.notificationpreferenceservice.model.UserNotificationOverride;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface UserNotificationOverrideRepository extends R2dbcRepository<UserNotificationOverride, Long> {
    Mono<UserNotificationOverride> findByUserIdAndNotificationTypeId(Long userId, Long notificationTypeId);
}
