package com.javaclaw.core.memory;

import java.time.Instant;

public record MemoryEntry(
        String id,
        String category,
        String key,
        String value,
        String source,
        Instant createdAt,
        Instant updatedAt
) {
}
