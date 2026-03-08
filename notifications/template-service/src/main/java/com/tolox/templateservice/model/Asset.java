package com.tolox.templateservice.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("assets")
public record Asset(
        @Id UUID id,
        String assetKey,
        String assetUrl,
        String description,
        @CreatedDate Instant createdAt,
        @LastModifiedDate Instant updatedAt
) {}
