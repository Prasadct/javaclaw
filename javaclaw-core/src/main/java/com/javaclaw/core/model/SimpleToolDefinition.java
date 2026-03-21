package com.javaclaw.core.model;

import java.util.function.Function;

public record SimpleToolDefinition(
        String name,
        String description,
        RiskLevel riskLevel,
        Function<String, String> executor
) implements ToolDefinition {

    public SimpleToolDefinition(String name, String description, Function<String, String> executor) {
        this(name, description, RiskLevel.LOW, executor);
    }
}
