package com.javaclaw.core.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.core.approval.ApprovalHandler;
import com.javaclaw.core.approval.ApprovalRequest;
import com.javaclaw.core.approval.ApprovalResult;
import com.javaclaw.core.approval.AutoApprovalHandler;
import com.javaclaw.core.channel.TaskProgressListener;
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
import java.util.function.Consumer;

public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);
    private static final int DEFAULT_MAX_STEPS = 10;
    private static final int MAX_HISTORY_ENTRIES = 12;
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
        return execute(agent, task, null);
    }

    public AgentTask execute(AgentDefinition agent, AgentTask task, TaskProgressListener listener) {
        String goal = task.getGoal();
        task.addAuditEvent(AuditEvent.of("TASK_CREATED", "Goal: " + goal));
        task.setStatus(TaskStatus.RUNNING);
        task.addAuditEvent(AuditEvent.of("TASK_STARTED", "Agent: " + agent.name()));
        notifyListener(listener, l -> l.onTaskStarted(task));

        String systemPrompt = buildSystemPrompt(agent);
        List<String> conversationHistory = new ArrayList<>();
        conversationHistory.add("Goal: " + goal);

        String lastAction = "";
        String lastInput = "";
        int consecutiveRepeats = 0;

        for (int i = 0; i < maxSteps; i++) {
            log.debug("Iteration {}/{} for task {}", i + 1, maxSteps, task.getId());

            if (conversationHistory.size() > MAX_HISTORY_ENTRIES) {
                int excess = conversationHistory.size() - MAX_HISTORY_ENTRIES;
                conversationHistory.subList(1, 1 + excess).clear();
                conversationHistory.add(1, "[Earlier conversation history trimmed — focus on recent context]");
            }

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
                notifyListener(listener, l -> l.onTaskFailed(task, task.getResult()));
                return task;
            }

            if (llmResponse == null || llmResponse.isBlank()) {
                log.warn("Empty LLM response on iteration {}", i + 1);
                task.setStatus(TaskStatus.FAILED);
                task.setResult("Empty response from LLM");
                task.addAuditEvent(AuditEvent.of("LLM_ERROR", "Empty response"));
                notifyListener(listener, l -> l.onTaskFailed(task, task.getResult()));
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
                notifyListener(listener, l -> l.onTaskFailed(task, task.getResult()));
                return task;
            }

            String thought = parsed.path("thought").asText("");
            String action = parsed.path("action").asText("");
            JsonNode inputNode = parsed.path("input");
            String input = inputNode.isTextual() ? inputNode.asText("") : inputNode.toString();

            log.info("Thought: {} | Action: {} | Input: {}", thought, action, input);
            task.addAuditEvent(AuditEvent.of("THOUGHT", thought));

            conversationHistory.add("You responded: " + llmResponse.strip());

            // Check for empty input (task_complete accepts plain strings, so exclude it)
            if (input.isBlank() && !"task_complete".equals(action)) {
                log.warn("LLM sent empty input for tool '{}', skipping execution", action);
                conversationHistory.add("Tool '" + action + "' was NOT executed because input was empty. " +
                        "You must provide the required fields. Check the tool description for required input format.");
                continue;
            }

            // Detect repeated calls
            if (action.equals(lastAction) && input.equals(lastInput)) {
                consecutiveRepeats++;
                if (consecutiveRepeats >= 1) {
                    log.warn("LLM repeated same call {} times: action={}, input={}", consecutiveRepeats, action, input);
                    conversationHistory.add("SYSTEM: You have repeated the same tool call " + consecutiveRepeats +
                            " times. You MUST try a DIFFERENT action or provide DIFFERENT input. " +
                            "Review what you have already accomplished and decide on the next step.");
                    continue;
                }
            } else {
                consecutiveRepeats = 0;
            }
            lastAction = action;
            lastInput = input;

            notifyListener(listener, l -> l.onToolRequested(task, action, input));

            // Look up tool
            var toolOpt = toolRegistry.get(action);
            if (toolOpt.isEmpty()) {
                log.error("Unknown tool requested: {}", action);
                task.setStatus(TaskStatus.FAILED);
                task.setResult("Unknown tool: " + action);
                task.addAuditEvent(AuditEvent.of("UNKNOWN_TOOL", action));
                notifyListener(listener, l -> l.onTaskFailed(task, task.getResult()));
                return task;
            }

            ToolDefinition tool = toolOpt.get();

            // Check policy
            PolicyDecision decision = policyEngine.evaluate(agent, action, input);
            task.addAuditEvent(AuditEvent.of("POLICY_CHECK", "tool=" + action + " decision=" + decision));
            log.info("Policy decision for tool '{}': {}", action, decision);
            notifyListener(listener, l -> l.onPolicyChecked(task, action, decision));

            switch (decision) {
                case DENY -> {
                    task.setStatus(TaskStatus.FAILED);
                    task.setResult("Policy denied tool: " + action);
                    task.addAuditEvent(AuditEvent.of("POLICY_DENIED", "Tool " + action + " denied by policy"));
                    log.warn("Tool '{}' denied by policy", action);
                    notifyListener(listener, l -> l.onTaskFailed(task, task.getResult()));
                    return task;
                }
                case REQUIRE_APPROVAL -> {
                    task.setStatus(TaskStatus.WAITING_FOR_APPROVAL);
                    var approvalRequest = new ApprovalRequest(
                            task.getId(), goal, action, input,
                            tool.riskLevel(),
                            tool.describeApprovalRequest(input),
                            tool.approvalMetadata(input)
                    );
                    task.addAuditEvent(AuditEvent.of("APPROVAL_REQUESTED",
                            "Tool " + action + " requires approval"));
                    log.info("Tool '{}' requires approval — waiting for human decision", action);
                    notifyListener(listener, l -> l.onApprovalRequested(task, approvalRequest));

                    ApprovalResult approvalResult = approvalHandler.handle(approvalRequest);

                    if (approvalResult.approved()) {
                        task.addAuditEvent(AuditEvent.of("APPROVAL_GRANTED",
                                "Tool " + action + " approved: " + approvalResult.reason()));
                        task.setStatus(TaskStatus.RUNNING);
                        log.info("Tool '{}' approved: {}", action, approvalResult.reason());
                        notifyListener(listener, l -> l.onApprovalDecision(task, approvalResult));
                    } else {
                        task.addAuditEvent(AuditEvent.of("APPROVAL_REJECTED",
                                "Tool " + action + " rejected: " + approvalResult.reason()));
                        task.setStatus(TaskStatus.CANCELLED);
                        task.setResult("Approval rejected for tool '" + action + "': " + approvalResult.reason());
                        log.info("Tool '{}' rejected: {}", action, approvalResult.reason());
                        notifyListener(listener, l -> l.onApprovalDecision(task, approvalResult));
                        notifyListener(listener, l -> l.onTaskFailed(task, task.getResult()));
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
                notifyListener(listener, l -> l.onToolExecuted(task, action, toolResult));
            } catch (Exception e) {
                log.error("Tool '{}' execution failed: {}", action, e.getMessage(), e);
                task.setStatus(TaskStatus.FAILED);
                task.setResult("Tool execution failed: " + e.getMessage());
                task.addAuditEvent(AuditEvent.of("TOOL_ERROR",
                        "tool=" + action + " error=" + e.getMessage()));
                notifyListener(listener, l -> l.onTaskFailed(task, task.getResult()));
                return task;
            }

            // Check if the executed tool was task_complete
            if ("task_complete".equals(action)) {
                task.setStatus(TaskStatus.COMPLETED);
                task.setResult(toolResult);
                task.addAuditEvent(AuditEvent.of("TASK_COMPLETED", toolResult));
                log.info("Task {} completed: {}", task.getId(), toolResult);
                notifyListener(listener, l -> l.onTaskCompleted(task));
                return task;
            }

            conversationHistory.add("Tool '" + action + "' returned: " + toolResult);
            if (toolResult.startsWith("ERROR:")) {
                conversationHistory.add("Note: The tool returned an error. Try a different approach or a different tool.");
            }
        }

        // Max iterations reached
        log.warn("Task {} reached max iterations ({})", task.getId(), maxSteps);
        task.setStatus(TaskStatus.FAILED);
        task.setResult("Max iterations (" + maxSteps + ") reached without completion");
        task.addAuditEvent(AuditEvent.of("MAX_ITERATIONS", "Reached " + maxSteps + " iterations"));
        notifyListener(listener, l -> l.onTaskFailed(task, task.getResult()));
        return task;
    }

    private void notifyListener(TaskProgressListener listener, Consumer<TaskProgressListener> callback) {
        if (listener != null) {
            try {
                callback.accept(listener);
            } catch (Exception e) {
                log.warn("Listener callback failed: {}", e.getMessage(), e);
            }
        }
    }

    private String buildSystemPrompt(AgentDefinition agent) {
        return agent.systemPrompt() + "\n\n" +
                toolRegistry.describeAll() + "\n" +
                """
                You must respond ONLY with a JSON object in this exact format:
                {"thought": "your reasoning", "action": "one of the available tools", "input": "<tool input>"}

                Rules:
                - "input" can be a plain string OR a JSON object, depending on what the tool expects.
                - If a tool returns an ERROR, do NOT repeat the same call. Analyze the error and try a different approach or tool.
                - Do not include any text outside the JSON object.""";
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
