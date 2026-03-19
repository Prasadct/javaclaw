package com.javaclaw.core.approval;

import java.time.Instant;

public record ApprovalResult(
        boolean approved,
        String reason,
        Instant decidedAt
) {
    public static ApprovalResult approved(String reason) {
        return new ApprovalResult(true, reason, Instant.now());
    }

    public static ApprovalResult rejected(String reason) {
        return new ApprovalResult(false, reason, Instant.now());
    }
}
