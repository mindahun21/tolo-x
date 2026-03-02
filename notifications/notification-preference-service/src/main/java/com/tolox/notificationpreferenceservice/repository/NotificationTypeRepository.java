package com.tolox.notificationpreferenceservice.repository;

import com.tolox.notificationpreferenceservice.model.NotificationType;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface NotificationTypeRepository extends R2dbcRepository<NotificationType, Long> {
    Mono<NotificationType> findByCode(String code);
    Mono<NotificationType> findByCodeAndAppId(String code, String appId);
}
