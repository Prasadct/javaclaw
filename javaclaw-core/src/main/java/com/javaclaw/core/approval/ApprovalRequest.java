package com.javaclaw.core.approval;

import com.javaclaw.core.model.RiskLevel;

import java.util.Map;

public record ApprovalRequest(
        String taskId,
        String goal,
        String toolName,
        String toolInput,
        RiskLevel riskLevel,
        String reason,
        Map<String, String> metadata
) {
    public ApprovalRequest(String taskId, String goal, String toolName, String toolInput,
                           RiskLevel riskLevel, String reason) {
        this(taskId, goal, toolName, toolInput, riskLevel, reason, Map.of());
    }
}
