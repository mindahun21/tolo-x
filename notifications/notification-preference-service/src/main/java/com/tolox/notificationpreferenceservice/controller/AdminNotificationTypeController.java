package com.tolox.notificationpreferenceservice.controller;

import com.tolox.notificationpreferenceservice.dto.NotificationTypeRequest;
import com.tolox.notificationpreferenceservice.dto.NotificationTypeResponse;
import com.tolox.notificationpreferenceservice.service.AdminNotificationTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/notification-preference/admin/notification-types")
@RequiredArgsConstructor
public class AdminNotificationTypeController {
    private final AdminNotificationTypeService service;

    @PostMapping
    public Mono<NotificationTypeResponse> create(@RequestBody NotificationTypeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{code}")
    public Mono<NotificationTypeResponse> update(@PathVariable String code, @RequestBody NotificationTypeRequest request) {
        return service.update(code, request);
    }

    @PatchMapping("/{code}/deprecate")
    public Mono<Map<String, Boolean>> deprecate(@PathVariable String code) {
        return service.deprecate(code).map(res -> Map.of("deprecated", res));
    }

    @GetMapping
    public Flux<NotificationTypeResponse> getAll() {
        return service.getAll();
    }
}
