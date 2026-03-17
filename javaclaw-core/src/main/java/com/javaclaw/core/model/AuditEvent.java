package com.javaclaw.core.model;

import java.time.Instant;

public record AuditEvent(
        Instant timestamp,
        String type,
        String detail
) {
    public static AuditEvent of(String type, String detail) {
        return new AuditEvent(Instant.now(), type, detail);
    }
}
