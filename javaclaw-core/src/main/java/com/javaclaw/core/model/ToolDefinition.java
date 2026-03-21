package com.javaclaw.core.model;

import java.util.Map;
import java.util.function.Function;

public interface ToolDefinition {

    String name();

    String description();

    RiskLevel riskLevel();

    Function<String, String> executor();

    default String describeApprovalRequest(String input) {
        return "Tool '" + name() + "' (risk: " + riskLevel() + ") requires approval.\nInput: " + input;
    }

    default Map<String, String> approvalMetadata(String input) {
        return Map.of();
    }
}
