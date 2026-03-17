package com.javaclaw.core.model;

import java.util.function.Function;

public record ToolDefinition(
        String name,
        String description,
        RiskLevel riskLevel,
        Function<String, String> executor
) {
    public ToolDefinition(String name, String description, Function<String, String> executor) {
        this(name, description, RiskLevel.LOW, executor);
    }
}
