package com.tolox.notificationpreferenceservice.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("user_notification_overrides")
public class UserNotificationOverride {
    
    @Id
    private Long id;

    @Column("user_id")
    private Long userId;
    
    @Column("notification_type_id")
    private Long notificationTypeId;

    @Column("is_enabled")
    private boolean isEnabled;

    @Column("consent_version")
    private String consentVersion;
    
    @Column("consent_timestamp")
    private Instant consentTimestamp;

    @Column("updated_at")
    private Instant updatedAt;
}
