package com.javaclaw.core.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CLIApprovalHandler implements ApprovalHandler {

    private static final Logger log = LoggerFactory.getLogger(CLIApprovalHandler.class);

    private final InputStream inputStream;
    private final PrintStream outputStream;
    private final int timeoutSeconds;

    public CLIApprovalHandler() {
        this(System.in, System.out, 300);
    }

    public CLIApprovalHandler(int timeoutSeconds) {
        this(System.in, System.out, timeoutSeconds);
    }

    public CLIApprovalHandler(InputStream inputStream, PrintStream outputStream, int timeoutSeconds) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public ApprovalResult handle(ApprovalRequest request) {
        printApprovalBox(request);

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "approval-input");
            t.setDaemon(true);
            return t;
        });

        try {
            Callable<ApprovalResult> inputTask = () -> readApprovalInput(request);
            Future<ApprovalResult> future = executor.submit(inputTask);
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Approval timed out after {} seconds for tool '{}' — auto-rejecting",
                    timeoutSeconds, request.toolName());
            outputStream.println("Approval timed out — auto-rejecting.");
            return ApprovalResult.rejected("Timed out after " + timeoutSeconds + " seconds");
        } catch (Exception e) {
            log.error("Error during approval input: {}", e.getMessage(), e);
            return ApprovalResult.rejected("Error reading approval input: " + e.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    private void printApprovalBox(ApprovalRequest request) {
        outputStream.println();
        outputStream.println("==================================================");
        outputStream.println("  APPROVAL REQUIRED");
        outputStream.println("==================================================");
        outputStream.println("  Task:       " + request.taskId());
        outputStream.println("  Goal:       " + request.goal());
        outputStream.println("  Tool:       " + request.toolName());
        outputStream.println("  Input:      " + request.toolInput());
        outputStream.println("  Risk Level: " + request.riskLevel());
        outputStream.println("  Reason:     " + request.reason());
        outputStream.println("==================================================");
        outputStream.println("  Type 'approve' (y/yes) or 'reject' (n/no):");
        outputStream.println("==================================================");
        outputStream.flush();
    }

    private ApprovalResult readApprovalInput(ApprovalRequest request) {
        Scanner scanner = new Scanner(inputStream);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().strip().toLowerCase();
            switch (line) {
                case "approve", "y", "yes" -> {
                    log.info("Tool '{}' approved by human", request.toolName());
                    return ApprovalResult.approved("Approved by human");
                }
                case "reject", "n", "no" -> {
                    log.info("Tool '{}' rejected by human", request.toolName());
                    return ApprovalResult.rejected("Rejected by human");
                }
                default -> {
                    outputStream.println("Invalid input '" + line + "'. Type 'approve' (y/yes) or 'reject' (n/no):");
                    outputStream.flush();
                }
            }
        }
        // Input stream ended without valid response
        return ApprovalResult.rejected("Input stream closed without response");
    }
}
