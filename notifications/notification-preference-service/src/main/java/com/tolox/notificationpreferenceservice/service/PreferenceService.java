package com.tolox.notificationpreferenceservice.service;

import com.tolox.notificationpreferenceservice.context.ContextLoader;
import com.tolox.notificationpreferenceservice.dto.*;
import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import com.tolox.notificationpreferenceservice.rule.RuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PreferenceService {
    private final ContextLoader contextLoader;
    private final RuleEngine ruleEngine;

    public Mono<EvaluateResponse> evaluate(EvaluateRequest evaluateRequest) {
        return contextLoader.load(
                evaluateRequest.userId(),
                evaluateRequest.notificationTypeCode(),
                evaluateRequest.channel(),
                evaluateRequest.currentTime()
        )
                .map(ruleEngine::evaluate)
                .map(result ->
                        new EvaluateResponse(
                                result.deliver(),
                                result.reason()
                        )
                );
    }

    public Mono<EffectiveContextResponse> getEffectiveContext(EffectiveContextRequest request) {
        // This is a simplified implementation. Real logic would iterate over channels
        // based on priority and find the first one that evaluates to deliver=true.
        // For now, let's assume PUSH -> EMAIL -> SMS priority
        return Flux.just(NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.SMS)
                .flatMap(channel -> evaluate(new EvaluateRequest(
                        request.userId().toString(),
                        request.notificationTypeCode(),
                        channel.name(),
                        Instant.now()
                )).map(res -> new EffectiveContextResponse(channel, res.deliver())))
                .filter(EffectiveContextResponse::allowed)
                .next()
                .defaultIfEmpty(new EffectiveContextResponse(null, false));
    }
}
