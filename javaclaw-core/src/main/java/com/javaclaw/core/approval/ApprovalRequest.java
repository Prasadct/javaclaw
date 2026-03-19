package com.javaclaw.core.approval;

import com.javaclaw.core.model.RiskLevel;

public record ApprovalRequest(
        String taskId,
        String goal,
        String toolName,
        String toolInput,
        RiskLevel riskLevel,
        String reason
) {
}
