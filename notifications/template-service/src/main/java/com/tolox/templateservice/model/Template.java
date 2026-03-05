package com.tolox.templateservice.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("templates")
public record Template(
        @Id UUID id,
        String applicationCode,
        String templateCode,
        String description,
        Integer activeVersionNumber,
        @CreatedDate Instant createdAt,
        @LastModifiedDate Instant updatedAt
)implements Persistable<UUID> {
    @Override
    public boolean isNew() {
        return createdAt == null; 
    }
    @Override
    public UUID getId() {
        return id;
    }
}
