CREATE TABLE role (
                      id SERIAL PRIMARY KEY,
                      name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO role (name) VALUES
                            ('ADMIN'),
                            ('USER');
