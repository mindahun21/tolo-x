package com.tolox.notificationpreferenceservice.controller;

import com.tolox.notificationpreferenceservice.dto.*;
import com.tolox.notificationpreferenceservice.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/notification-preference/internal/preferences")
@RequiredArgsConstructor
public class PreferenceController {
    private final PreferenceService preferenceService;

    @PostMapping("/evaluate")
    public Mono<EvaluateResponse> evaluate(@RequestBody EvaluateRequest evaluateRequest) {
        return preferenceService.evaluate(evaluateRequest);
    }

    @PostMapping("/effective")
    public Mono<EffectiveContextResponse> getEffectiveContext(@RequestBody EffectiveContextRequest request) {
        return preferenceService.getEffectiveContext(request);
    }
}
