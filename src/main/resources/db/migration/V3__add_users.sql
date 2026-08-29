CREATE TYPE user_role AS ENUM ('ADMIN', 'USER', 'VIEWER');

CREATE TABLE users
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(72)  NOT NULL,
    role          user_role    NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username UNIQUE (username)
);

-- Static accounts for the demo. BCrypt hashes of the passwords documented in the README:
-- admin/admin123, user/user123, viewer/viewer123.
INSERT INTO users (username, password_hash, role)
VALUES ('admin', '$2b$10$JOYSZ.CLhhotwrV97sllGe0p9Pt.5cPAmgERguPeRQuO5kwL6qevy', 'ADMIN'),
       ('user', '$2b$10$Mm9n2HpHS69za0bXqN3CKOjjB1qGb11k1RkcVH37RFDTNbghydvlW', 'USER'),
       ('viewer', '$2b$10$dCy61OUaROFDVIAab577YexRVvT63I4.cOWFjSCHmVUXmbeixrNFy', 'VIEWER');
