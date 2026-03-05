CREATE TABLE IF NOT EXISTS templates (
                                         id UUID PRIMARY KEY,
                                         application_code VARCHAR(50) NOT NULL,
                                         template_code VARCHAR(100) NOT NULL,
                                         description TEXT,
                                         active_version_number INTEGER NOT NULL DEFAULT 1,
                                         created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         CONSTRAINT uk_template_identity UNIQUE(application_code, template_code)
);

-- Immutable Template Versions
CREATE TABLE IF NOT EXISTS template_versions (
                                                 id UUID PRIMARY KEY,
                                                 template_id UUID NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
                                                 version_number INTEGER NOT NULL,
                                                 channel VARCHAR(20) NOT NULL, -- EMAIL, SMS, PUSH
                                                 locale VARCHAR(10) NOT NULL,  -- en, am, etc.
                                                 subject TEXT,                 -- Nullable for SMS/PUSH
                                                 body TEXT NOT NULL,
                                                 engine VARCHAR(20) NOT NULL,  -- HANDLEBARS
                                                 status VARCHAR(20) NOT NULL,  -- DRAFT, PUBLISHED
                                                 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 CONSTRAINT uk_template_version_identity UNIQUE(template_id, version_number, channel, locale)
);

-- Index for high-throughput lookup
CREATE INDEX IF NOT EXISTS idx_template_version_lookup
    ON template_versions(template_id, version_number, channel, locale);