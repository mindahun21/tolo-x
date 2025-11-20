CREATE TABLE app_user (
                          id SERIAL PRIMARY KEY,
                          email VARCHAR(255) NOT NULL UNIQUE,
                          name VARCHAR(255),
                          password VARCHAR(255) NOT NULL,
                          enabled BOOLEAN NOT NULL DEFAULT TRUE,
                          image_url VARCHAR(500),
                          provider VARCHAR(50),
                          provider_id VARCHAR(255)
);