-- ===============================
-- 1. notification_type (Registry Layer)
-- ===============================

CREATE TABLE notification_type (
                                   id BIGSERIAL PRIMARY KEY,

                                   code VARCHAR(100) NOT NULL,
                                   app_id VARCHAR(100) NOT NULL,

                                   channel VARCHAR(20) NOT NULL,
                                   category VARCHAR(20) NOT NULL,

                                   default_enabled BOOLEAN NOT NULL,
                                   is_mandatory BOOLEAN NOT NULL,

                                   max_frequency_per_day INTEGER,
                                   cooldown_seconds INTEGER,

                                   status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   deprecated_at TIMESTAMP NULL,

                                   CONSTRAINT uk_notification_type_code UNIQUE (code)
);

-- Important indexes
CREATE INDEX idx_notification_type_app ON notification_type(app_id);
CREATE INDEX idx_notification_type_channel ON notification_type(channel);
CREATE INDEX idx_notification_type_status ON notification_type(status);



-- ===============================
-- 2. user_channel_settings (Global Layer)
-- ===============================

CREATE TABLE user_channel_settings (
                                       id BIGSERIAL PRIMARY KEY,
                                       user_id BIGINT NOT NULL,
                                       channel VARCHAR(20) NOT NULL,

                                       is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
                                       legal_opt_out BOOLEAN NOT NULL DEFAULT FALSE,

                                       quiet_hours_start TIME,
                                       quiet_hours_end TIME,
                                       timezone VARCHAR(50),

                                       consent_version VARCHAR(50),
                                       consent_timestamp TIMESTAMP,

                                       CONSTRAINT uk_user_channel UNIQUE (user_id, channel)
);

-- Critical for user lookups
CREATE INDEX idx_user_channel_settings_user ON user_channel_settings(user_id);



-- ===============================
-- 3. user_notification_overrides (Sparse Layer)
-- ===============================

CREATE TABLE user_notification_overrides (
                                             id BIGSERIAL PRIMARY KEY,
                                             user_id BIGINT NOT NULL,
                                             notification_type_id BIGINT NOT NULL,

                                             is_enabled BOOLEAN NOT NULL,

                                             consent_version VARCHAR(50),
                                             consent_timestamp TIMESTAMP,

                                             updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                             CONSTRAINT uk_user_override UNIQUE (user_id, notification_type_id),

                                             CONSTRAINT fk_override_notification_type
                                                 FOREIGN KEY (notification_type_id)
                                                     REFERENCES notification_type(id)
);

-- Critical indexes
CREATE INDEX idx_override_user ON user_notification_overrides(user_id);
CREATE INDEX idx_override_notification_type ON user_notification_overrides(notification_type_id);