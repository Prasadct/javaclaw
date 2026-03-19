package com.javaclaw.core.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.core.approval.ApprovalHandler;
import com.javaclaw.core.approval.ApprovalRequest;
import com.javaclaw.core.approval.ApprovalResult;
import com.javaclaw.core.approval.AutoApprovalHandler;
import com.javaclaw.core.model.AgentDefinition;
import com.javaclaw.core.model.AgentTask;
import com.javaclaw.core.model.AuditEvent;
import com.javaclaw.core.model.TaskStatus;
import com.javaclaw.core.model.ToolDefinition;
import com.javaclaw.core.policy.PolicyDecision;
import com.javaclaw.core.policy.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;

public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);
    private static final int DEFAULT_MAX_STEPS = 10;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final PolicyEngine policyEngine;
    private final ApprovalHandler approvalHandler;
    private final int maxSteps;

    public AgentRuntime(ChatClient chatClient, ToolRegistry toolRegistry, PolicyEngine policyEngine) {
        this(chatClient, toolRegistry, policyEngine, new AutoApprovalHandler(), DEFAULT_MAX_STEPS);
    }

    public AgentRuntime(ChatClient chatClient, ToolRegistry toolRegistry, PolicyEngine policyEngine, int maxSteps) {
        this(chatClient, toolRegistry, policyEngine, new AutoApprovalHandler(), maxSteps);
    }

    public AgentRuntime(ChatClient chatClient, ToolRegistry toolRegistry, PolicyEngine policyEngine,
                        ApprovalHandler approvalHandler, int maxSteps) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
        this.policyEngine = policyEngine;
        this.approvalHandler = approvalHandler;
        this.maxSteps = maxSteps;
    }

    public AgentTask execute(AgentDefinition agent, AgentTask task) {
        String goal = task.getGoal();
        task.addAuditEvent(AuditEvent.of("TASK_CREATED", "Goal: " + goal));
        task.setStatus(TaskStatus.RUNNING);
        task.addAuditEvent(AuditEvent.of("TASK_STARTED", "Agent: " + agent.name()));

        String systemPrompt = buildSystemPrompt(agent);
        List<String> conversationHistory = new ArrayList<>();
        conversationHistory.add("Goal: " + goal);

        for (int i = 0; i < maxSteps; i++) {
            log.debug("Iteration {}/{} for task {}", i + 1, maxSteps, task.getId());

            String userMessage = String.join("\n", conversationHistory);
            String llmResponse;
            try {
                llmResponse = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .call()
                        .content();
            } catch (Exception e) {
                log.error("LLM call failed on iteration {}: {}", i + 1, e.getMessage(), e);
                task.setStatus(TaskStatus.FAILED);
                task.setResult("LLM call failed: " + e.getMessage());
                task.addAuditEvent(AuditEvent.of("LLM_ERROR", e.getMessage()));
                return task;
            }

            if (llmResponse == null || llmResponse.isBlank()) {
                log.warn("Empty LLM response on iteration {}", i + 1);
                task.setStatus(TaskStatus.FAILED);
                task.setResult("Empty response from LLM");
                task.addAuditEvent(AuditEvent.of("LLM_ERROR", "Empty response"));
                return task;
            }

            task.addAuditEvent(AuditEvent.of("LLM_RESPONSE", llmResponse));

            JsonNode parsed;
            try {
                parsed = parseResponse(llmResponse);
            } catch (Exception e) {
                log.error("Failed to parse LLM response: {}", e.getMessage());
                task.setStatus(TaskStatus.FAILED);
                task.setResult("Failed to parse LLM response: " + e.getMessage());
                task.addAuditEvent(AuditEvent.of("PARSE_ERROR", e.getMessage()));
                return task;
            }

            String thought = parsed.path("thought").asText("");
            String action = parsed.path("action").asText("");
            String input = parsed.path("input").asText("");

            log.info("Thought: {} | Action: {} | Input: {}", thought, action, input);
            task.addAuditEvent(AuditEvent.of("THOUGHT", thought));

            if ("finish".equalsIgnoreCase(action)) {
                task.setStatus(TaskStatus.COMPLETED);
                task.setResult(input);
                task.addAuditEvent(AuditEvent.of("TASK_COMPLETED", input));
                log.info("Task {} completed: {}", task.getId(), input);
                return task;
            }

            // Look up tool
            var toolOpt = toolRegistry.get(action);
            if (toolOpt.isEmpty()) {
                log.error("Unknown tool requested: {}", action);
                task.setStatus(TaskStatus.FAILED);
                task.setResult("Unknown tool: " + action);
                task.addAuditEvent(AuditEvent.of("UNKNOWN_TOOL", action));
                return task;
            }

            ToolDefinition tool = toolOpt.get();

            // Check policy
            PolicyDecision decision = policyEngine.evaluate(agent, action, input);
            task.addAuditEvent(AuditEvent.of("POLICY_CHECK", "tool=" + action + " decision=" + decision));
            log.info("Policy decision for tool '{}': {}", action, decision);

            switch (decision) {
                case DENY -> {
                    task.setStatus(TaskStatus.FAILED);
                    task.setResult("Policy denied tool: " + action);
                    task.addAuditEvent(AuditEvent.of("POLICY_DENIED", "Tool " + action + " denied by policy"));
                    log.warn("Tool '{}' denied by policy", action);
                    return task;
                }
                case REQUIRE_APPROVAL -> {
                    task.setStatus(TaskStatus.WAITING_FOR_APPROVAL);
                    var approvalRequest = new ApprovalRequest(
                            task.getId(), goal, action, input,
                            tool.riskLevel(),
                            "Policy requires approval for tool '" + action + "'"
                    );
                    task.addAuditEvent(AuditEvent.of("APPROVAL_REQUESTED",
                            "Tool " + action + " requires approval"));
                    log.info("Tool '{}' requires approval — waiting for human decision", action);

                    ApprovalResult approvalResult = approvalHandler.handle(approvalRequest);

                    if (approvalResult.approved()) {
                        task.addAuditEvent(AuditEvent.of("APPROVAL_GRANTED",
                                "Tool " + action + " approved: " + approvalResult.reason()));
                        task.setStatus(TaskStatus.RUNNING);
                        log.info("Tool '{}' approved: {}", action, approvalResult.reason());
                    } else {
                        task.addAuditEvent(AuditEvent.of("APPROVAL_REJECTED",
                                "Tool " + action + " rejected: " + approvalResult.reason()));
                        task.setStatus(TaskStatus.CANCELLED);
                        task.setResult("Approval rejected for tool '" + action + "': " + approvalResult.reason());
                        log.info("Tool '{}' rejected: {}", action, approvalResult.reason());
                        return task;
                    }
                }
                case ALLOW -> {
                    // proceed
                }
            }

            // Execute tool
            String toolResult;
            try {
                toolResult = tool.executor().apply(input);
                task.addAuditEvent(AuditEvent.of("TOOL_EXECUTED",
                        "tool=" + action + " result=" + toolResult));
                log.info("Tool '{}' executed successfully", action);
            } catch (Exception e) {
                log.error("Tool '{}' execution failed: {}", action, e.getMessage(), e);
                task.setStatus(TaskStatus.FAILED);
                task.setResult("Tool execution failed: " + e.getMessage());
                task.addAuditEvent(AuditEvent.of("TOOL_ERROR",
                        "tool=" + action + " error=" + e.getMessage()));
                return task;
            }

            conversationHistory.add("Tool '" + action + "' returned: " + toolResult);
        }

        // Max iterations reached
        log.warn("Task {} reached max iterations ({})", task.getId(), maxSteps);
        task.setStatus(TaskStatus.FAILED);
        task.setResult("Max iterations (" + maxSteps + ") reached without completion");
        task.addAuditEvent(AuditEvent.of("MAX_ITERATIONS", "Reached " + maxSteps + " iterations"));
        return task;
    }

    private String buildSystemPrompt(AgentDefinition agent) {
        return agent.systemPrompt() + "\n\n" +
                toolRegistry.describeAll() + "\n" +
                """
                You must respond ONLY with a JSON object in this exact format:
                {"thought": "your reasoning", "action": "tool_name or finish", "input": "the input for the tool, or the final answer if action is finish"}

                Do not include any text outside the JSON object.
                """;
    }

    private JsonNode parseResponse(String response) throws Exception {
        String trimmed = response.strip();
        // Handle responses wrapped in markdown code fences
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }
        JsonNode node = objectMapper.readTree(trimmed);
        if (!node.has("action")) {
            throw new IllegalArgumentException("Response JSON missing 'action' field");
        }
        return node;
    }
}
