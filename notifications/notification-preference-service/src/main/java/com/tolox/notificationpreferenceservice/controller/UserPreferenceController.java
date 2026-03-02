package com.tolox.notificationpreferenceservice.controller;

import com.tolox.notificationpreferenceservice.dto.UserChannelSettingsRequest;
import com.tolox.notificationpreferenceservice.dto.UserChannelSettingsResponse;
import com.tolox.notificationpreferenceservice.dto.UserNotificationOverrideRequest;
import com.tolox.notificationpreferenceservice.dto.UserNotificationOverrideResponse;
import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import com.tolox.notificationpreferenceservice.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/users/{userId}")
@RequiredArgsConstructor
public class UserPreferenceController {
    private final UserPreferenceService service;

    @GetMapping("/channels")
    public Flux<UserChannelSettingsResponse> getChannels(@PathVariable Long userId) {
        return service.getChannels(userId);
    }

    @PutMapping("/channels/{channel}")
    public Mono<Map<String, Boolean>> updateChannel(
            @PathVariable Long userId,
            @PathVariable NotificationChannel channel,
            @RequestBody UserChannelSettingsRequest request) {
        return service.updateChannel(userId, channel, request).map(res -> Map.of("updated", res));
    }

    @PatchMapping("/channels/{channel}/legal-opt-out")
    public Mono<Map<String, Boolean>> setLegalOptOut(
            @PathVariable Long userId,
            @PathVariable NotificationChannel channel,
            @RequestBody Map<String, Boolean> body) {
        return service.setLegalOptOut(userId, channel, body.get("value")).map(res -> Map.of("updated", res));
    }

    @GetMapping("/notifications")
    public Flux<UserNotificationOverrideResponse> getOverrides(@PathVariable Long userId) {
        return service.getOverrides(userId);
    }

    @PutMapping("/notifications/{code}")
    public Mono<Map<String, Boolean>> updateOverride(
            @PathVariable Long userId,
            @PathVariable String code,
            @RequestBody UserNotificationOverrideRequest request) {
        return service.updateOverride(userId, code, request).map(res -> Map.of("updated", res));
    }

    @DeleteMapping("/notifications/{code}")
    public Mono<Map<String, Boolean>> deleteOverride(
            @PathVariable Long userId,
            @PathVariable String code) {
        return service.deleteOverride(userId, code).map(res -> Map.of("deleted", res));
    }
}
