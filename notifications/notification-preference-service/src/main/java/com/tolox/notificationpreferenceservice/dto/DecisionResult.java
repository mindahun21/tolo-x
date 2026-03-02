package com.tolox.notificationpreferenceservice.dto;

public record DecisionResult(
        boolean deliver,
        String reason,
        boolean decisive
) {}
