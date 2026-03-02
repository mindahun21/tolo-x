package com.tolox.notificationpreferenceservice.service;

import com.tolox.notificationpreferenceservice.dto.NotificationTypeRequest;
import com.tolox.notificationpreferenceservice.dto.NotificationTypeResponse;
import com.tolox.notificationpreferenceservice.enums.NotificationStatus;
import com.tolox.notificationpreferenceservice.model.NotificationType;
import com.tolox.notificationpreferenceservice.repository.NotificationTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminNotificationTypeService {
    private final NotificationTypeRepository repository;

    public Mono<NotificationTypeResponse> create(NotificationTypeRequest request) {
        NotificationType type = new NotificationType();
        type.setCode(request.code());
        type.setAppId(request.appId());
        type.setChannel(request.channel());
        type.setCategory(request.category());
        type.setDefaultEnabled(request.defaultEnabled());
        type.setMandatory(request.mandatory());
        type.setMaxFrequencyPerDay(request.maxFrequencyPerDay());
        type.setCooldownSeconds(request.cooldownSeconds());
        type.setStatus(request.status());
        type.setCreatedAt(Instant.now());

        return repository.save(type).map(this::mapToResponse);
    }

    public Mono<NotificationTypeResponse> update(String code, NotificationTypeRequest request) {
        return repository.findByCode(code)
                .flatMap(type -> {
                    if (request.defaultEnabled() != type.isDefaultEnabled()) {
                        type.setDefaultEnabled(request.defaultEnabled());
                    }
                    if (request.mandatory() != type.isMandatory()) {
                        type.setMandatory(request.mandatory());
                    }
                    if (request.maxFrequencyPerDay() != null) {
                        type.setMaxFrequencyPerDay(request.maxFrequencyPerDay());
                    }
                    // Add other fields as necessary
                    return repository.save(type);
                })
                .map(this::mapToResponse);
    }

    public Mono<Boolean> deprecate(String code) {
        return repository.findByCode(code)
                .flatMap(type -> {
                    type.setStatus(NotificationStatus.DEPRECATED);
                    type.setDeprecatedAt(Instant.now());
                    return repository.save(type).thenReturn(true);
                })
                .defaultIfEmpty(false);
    }

    public Flux<NotificationTypeResponse> getAll() {
        return repository.findAll().map(this::mapToResponse);
    }

    private NotificationTypeResponse mapToResponse(NotificationType type) {
        return NotificationTypeResponse.builder()
                .id(type.getId())
                .code(type.getCode())
                .appId(type.getAppId())
                .channel(type.getChannel())
                .category(type.getCategory())
                .defaultEnabled(type.isDefaultEnabled())
                .mandatory(type.isMandatory())
                .maxFrequencyPerDay(type.getMaxFrequencyPerDay())
                .cooldownSeconds(type.getCooldownSeconds())
                .status(type.getStatus())
                .createdAt(type.getCreatedAt())
                .deprecatedAt(type.getDeprecatedAt())
                .build();
    }
}
