package com.javaclaw.core.runtime;

import com.javaclaw.core.model.AgentDefinition;
import com.javaclaw.core.model.AgentTask;
import com.javaclaw.core.model.TaskStatus;
import com.javaclaw.core.model.ToolDefinition;
import com.javaclaw.core.policy.PolicyDecision;
import com.javaclaw.core.policy.PolicyEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentRuntimeTest {

    private ToolRegistry toolRegistry;
    private PolicyEngine policyEngine;
    private AgentDefinition agent;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        policyEngine = mock(PolicyEngine.class);
        agent = new AgentDefinition("test-agent", "You are a test agent.", List.of("lookup"));
    }

    @Test
    void happyPath_toolCalledThenFinish() {
        // Register a tool
        toolRegistry.register(new ToolDefinition("lookup", "Looks up data", input -> "result-for-" + input));

        // Policy allows everything
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        // Mock ChatClient: first call returns tool invocation, second returns finish
        var callCounter = new AtomicInteger(0);
        ChatClient chatClient = mockChatClient(() -> {
            int call = callCounter.getAndIncrement();
            if (call == 0) {
                return """
                        {"thought": "I need to look up the data", "action": "lookup", "input": "key1"}
                        """;
            } else {
                return """
                        {"thought": "I have the result", "action": "finish", "input": "The answer is result-for-key1"}
                        """;
            }
        });

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, "Find the answer");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getResult()).isEqualTo("The answer is result-for-key1");
        assertThat(task.getAuditTrail()).isNotEmpty();

        // Verify policy was checked
        verify(policyEngine).evaluate(agent, "lookup", "key1");

        // Verify audit trail contains expected events
        var eventTypes = task.getAuditTrail().stream().map(e -> e.type()).toList();
        assertThat(eventTypes).contains("TASK_CREATED", "TASK_STARTED", "LLM_RESPONSE",
                "THOUGHT", "POLICY_CHECK", "TOOL_EXECUTED", "TASK_COMPLETED");
    }

    @Test
    void policyDeny_stopsExecution() {
        toolRegistry.register(new ToolDefinition("dangerous", "A dangerous tool", input -> "should not run"));

        when(policyEngine.evaluate(any(), eq("dangerous"), any())).thenReturn(PolicyDecision.DENY);

        ChatClient chatClient = mockChatClient(() ->
                """
                {"thought": "Using dangerous tool", "action": "dangerous", "input": "payload"}
                """);

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, "Do something dangerous");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getResult()).contains("Policy denied");

        var eventTypes = task.getAuditTrail().stream().map(e -> e.type()).toList();
        assertThat(eventTypes).contains("POLICY_CHECK", "POLICY_DENIED");
    }

    @Test
    void requireApproval_autoApprovesAndContinues() {
        toolRegistry.register(new ToolDefinition("sensitive", "Sensitive operation", input -> "done"));

        when(policyEngine.evaluate(any(), eq("sensitive"), any())).thenReturn(PolicyDecision.REQUIRE_APPROVAL);

        var callCounter = new AtomicInteger(0);
        ChatClient chatClient = mockChatClient(() -> {
            int call = callCounter.getAndIncrement();
            if (call == 0) {
                return """
                        {"thought": "Running sensitive op", "action": "sensitive", "input": "data"}
                        """;
            } else {
                return """
                        {"thought": "Done", "action": "finish", "input": "Operation completed"}
                        """;
            }
        });

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, "Run sensitive operation");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getResult()).isEqualTo("Operation completed");

        var eventTypes = task.getAuditTrail().stream().map(e -> e.type()).toList();
        assertThat(eventTypes).contains("APPROVAL_REQUIRED", "AUTO_APPROVED", "TOOL_EXECUTED");
    }

    @Test
    void unknownTool_failsGracefully() {
        // No tools registered
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        ChatClient chatClient = mockChatClient(() ->
                """
                {"thought": "Trying nonexistent tool", "action": "nonexistent", "input": "data"}
                """);

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, "Use unknown tool");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getResult()).contains("Unknown tool");
    }

    @Test
    void malformedLlmResponse_failsGracefully() {
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        ChatClient chatClient = mockChatClient(() -> "this is not json at all");

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, "Bad response");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getResult()).contains("Failed to parse");
    }

    @FunctionalInterface
    interface ResponseSupplier {
        String get();
    }

    /**
     * Creates a mock ChatClient using Mockito's deep-stubs to simulate
     * the fluent API: chatClient.prompt().system(...).user(...).call().content()
     */
    private ChatClient mockChatClient(ResponseSupplier responseSupplier) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_DEEP_STUBS);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenAnswer(invocation -> responseSupplier.get());

        return chatClient;
    }
}
