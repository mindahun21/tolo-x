CREATE TABLE assets (
    id UUID PRIMARY KEY,
    asset_key VARCHAR(100) UNIQUE NOT NULL,
    asset_url TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Add sample assets for testing
INSERT INTO assets (id, asset_key, asset_url, description) 
VALUES (gen_random_uuid(), 'COMPANY_LOGO', 'https://tolo-x.com/images/logo.png', 'Official company logo v2');

INSERT INTO assets (id, asset_key, asset_url, description) 
VALUES (gen_random_uuid(), 'SUPPORT_ICON', 'https://cdn.tolo-x.com/icons/support.png', 'Customer support icon');
