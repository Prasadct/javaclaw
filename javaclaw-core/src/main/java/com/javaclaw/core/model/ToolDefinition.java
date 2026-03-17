package com.javaclaw.core.model;

import java.util.function.Function;

public record ToolDefinition(
        String name,
        String description,
        Function<String, String> executor
) {
}
