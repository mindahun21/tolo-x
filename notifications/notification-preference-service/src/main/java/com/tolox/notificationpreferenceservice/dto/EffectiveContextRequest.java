package com.tolox.notificationpreferenceservice.dto;

import lombok.Builder;

@Builder
public record EffectiveContextRequest(
        Long userId,
        String notificationTypeCode
) {}
