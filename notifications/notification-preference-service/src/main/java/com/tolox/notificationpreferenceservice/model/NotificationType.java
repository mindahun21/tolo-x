package com.tolox.notificationpreferenceservice.model;
import com.tolox.notificationpreferenceservice.enums.NotificationCategory;
import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import com.tolox.notificationpreferenceservice.enums.NotificationStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;


@Data
@Table("notification_type")
public class NotificationType {

    @Id
    private Long id;

    private String code;

    @Column("app_id")
    private String appId;

    private NotificationChannel channel;

    private NotificationCategory category;

    @Column("default_enabled")
    private boolean defaultEnabled;

    @Column("is_mandatory")
    private boolean isMandatory;

    @Column("max_frequency_per_day")
    private Integer maxFrequencyPerDay;

    @Column("cooldown_seconds")
    private Integer cooldownSeconds;

    private NotificationStatus status;

    @Column("created_at")
    private Instant createdAt;

    @Column("deprecated_at")
    private Instant deprecatedAt;
}
