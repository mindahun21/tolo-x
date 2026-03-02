package com.tolox.notificationpreferenceservice.model;

import com.tolox.notificationpreferenceservice.enums.NotificationChannel;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalTime;

@Data
@Table("user_channel_settings")
public class UserChannelSettings {
    @Id
    private Long id;

    @Column("user_id")
    private Long userId;
    
    @Column("channel")
    private NotificationChannel channel;

    @Column("is_blocked")
    private boolean isBlocked;
    
    @Column("legal_opt_out")
    private boolean legalOptOut;

    @Column("quiet_hours_start")
    private LocalTime quietHoursStart;
    
    @Column("quiet_hours_end")
    private LocalTime quietHoursEnd;

    private String timezone;

    @Column("consent_version")
    private String consentVersion;
    
    @Column("consent_timestamp")
    private Instant consentTimestamp;
}
