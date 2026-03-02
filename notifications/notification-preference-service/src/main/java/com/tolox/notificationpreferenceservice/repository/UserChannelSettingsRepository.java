package com.tolox.notificationpreferenceservice.repository;

import com.tolox.notificationpreferenceservice.model.UserChannelSettings;
import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface UserChannelSettingsRepository extends R2dbcRepository<UserChannelSettings, Long> {
    Mono<UserChannelSettings> findByUserIdAndChannel(Long userId, NotificationChannel channel);
}
