package com.javaclaw.core.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AsyncApprovalHandler implements ApprovalHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncApprovalHandler.class);

    private final int timeoutSeconds;
    private final ConcurrentHashMap<String, CompletableFuture<ApprovalResult>> pendingFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ApprovalRequest> pendingRequests = new ConcurrentHashMap<>();

    public AsyncApprovalHandler(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public ApprovalResult handle(ApprovalRequest request) {
        String requestId = request.taskId();
        var future = new CompletableFuture<ApprovalResult>();
        pendingFutures.put(requestId, future);
        pendingRequests.put(requestId, request);
        log.info("Approval requested for '{}' (requestId={}), waiting up to {} seconds",
                request.toolName(), requestId, timeoutSeconds);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Approval timed out for requestId={} after {} seconds — auto-rejecting",
                    requestId, timeoutSeconds);
            return ApprovalResult.rejected("Timed out after " + timeoutSeconds + " seconds");
        } catch (Exception e) {
            log.error("Error waiting for approval requestId={}: {}", requestId, e.getMessage(), e);
            return ApprovalResult.rejected("Error waiting for approval: " + e.getMessage());
        } finally {
            pendingFutures.remove(requestId);
            pendingRequests.remove(requestId);
        }
    }

    public boolean submitDecision(String requestId, ApprovalResult result) {
        var future = pendingFutures.get(requestId);
        if (future == null) {
            log.warn("No pending approval found for requestId={}", requestId);
            return false;
        }
        return future.complete(result);
    }

    public Map<String, ApprovalRequest> getPendingApprovals() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(pendingRequests));
    }
}
