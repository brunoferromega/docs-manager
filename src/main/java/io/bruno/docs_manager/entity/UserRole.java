package io.bruno.docs_manager.entity;

/** Mirrors the {@code user_role} Postgres enum. */
public enum UserRole {
    /** Full access, including deletion. */
    ADMIN,
    /** Creates and edits documents and file versions. */
    USER,
    /** Read-only. */
    VIEWER
}
