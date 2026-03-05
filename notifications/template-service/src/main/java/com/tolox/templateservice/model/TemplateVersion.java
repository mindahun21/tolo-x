package com.tolox.templateservice.model;

import com.tolox.templateservice.enums.ChannelType;
import com.tolox.templateservice.enums.TemplateEngine;
import com.tolox.templateservice.enums.VersionStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("template_versions")
public record TemplateVersion(
        @Id UUID id,
        UUID templateId,
        Integer versionNumber,
        ChannelType channel,
        String locale,
        String subject,
        String body,
        TemplateEngine engine,
        VersionStatus status,
        @CreatedDate Instant createdAt
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