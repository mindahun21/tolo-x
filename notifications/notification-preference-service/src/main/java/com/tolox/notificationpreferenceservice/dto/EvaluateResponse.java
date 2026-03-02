package com.tolox.notificationpreferenceservice.dto;

public record EvaluateResponse(
        boolean deliver,
        String reason
) {}
