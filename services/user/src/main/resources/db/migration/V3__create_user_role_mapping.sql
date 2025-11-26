CREATE TABLE app_user_role_mapping (
                                      user_id BIGINT NOT NULL,
                                      role_id BIGINT NOT NULL,

                                      PRIMARY KEY (user_id, role_id),

                                      CONSTRAINT fk_user
                                          FOREIGN KEY (user_id)
                                              REFERENCES app_user (id)
                                              ON DELETE CASCADE,

                                      CONSTRAINT fk_role
                                          FOREIGN KEY (role_id)
                                              REFERENCES role (id)
                                              ON DELETE CASCADE
);
