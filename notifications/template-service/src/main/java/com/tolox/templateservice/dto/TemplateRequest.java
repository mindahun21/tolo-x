package com.tolox.templateservice.dto;

import java.util.UUID;

public record TemplateRequest(
        String applicationCode,
        String templateCode,
        String description
) {}
