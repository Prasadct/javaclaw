package com.javaclaw.core.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoApprovalHandler implements ApprovalHandler {

    private static final Logger log = LoggerFactory.getLogger(AutoApprovalHandler.class);

    @Override
    public ApprovalResult handle(ApprovalRequest request) {
        log.warn("Auto-approving action '{}' for task {} — NOT FOR PRODUCTION USE",
                request.toolName(), request.taskId());
        return ApprovalResult.approved("Auto-approved");
    }
}
