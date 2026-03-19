package com.javaclaw.core.approval;

public interface ApprovalHandler {
    ApprovalResult handle(ApprovalRequest request);
}
