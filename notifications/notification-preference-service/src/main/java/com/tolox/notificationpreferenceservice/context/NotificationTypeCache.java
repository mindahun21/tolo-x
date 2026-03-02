package com.tolox.notificationpreferenceservice.context;

import com.tolox.notificationpreferenceservice.model.NotificationType;
import com.tolox.notificationpreferenceservice.repository.NotificationTypeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationTypeCache {

    private final NotificationTypeRepository repository;
    private final Map<String, NotificationType> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedRate = 300000) // Refresh every 5 minutes
    public void refresh() {
        log.info("Refreshing L1 Cache: Notification Registry");
        repository.findAll()
                .doOnNext(type -> cache.put(type.getCode(), type))
                .subscribe();
    }

    public Mono<NotificationType> get(String code) {
        NotificationType type = cache.get(code);
        if (type != null) {
            return Mono.just(type);
        }
        // Fallback to DB if cache miss
        log.info("L1 Cache MISS: Fetching Notification Registry from DB for {}", code);
        return repository.findByCode(code)
                .doOnNext(nt -> cache.put(code, nt));
    }
}
