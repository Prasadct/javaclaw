package com.javaclaw.core.runtime;

import com.javaclaw.core.approval.ApprovalHandler;
import com.javaclaw.core.approval.ApprovalResult;
import com.javaclaw.core.channel.TaskProgressListener;
import com.javaclaw.core.model.AgentDefinition;
import com.javaclaw.core.model.AgentTask;
import com.javaclaw.core.model.TaskStatus;
import com.javaclaw.core.model.SimpleToolDefinition;
import com.javaclaw.core.model.ToolDefinition;
import com.javaclaw.core.policy.PolicyDecision;
import com.javaclaw.core.policy.PolicyEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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
        toolRegistry.register(new SimpleToolDefinition("task_complete", "Complete the task", input -> input));
        agent = new AgentDefinition("test-agent", "You are a test agent.", List.of("lookup", "task_complete"));
    }

    @Test
    void happyPath_toolCalledThenFinish() {
        // Register a tool
        toolRegistry.register(new SimpleToolDefinition("lookup", "Looks up data", input -> "result-for-" + input));

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
                        {"thought": "I have the result", "action": "task_complete", "input": "The answer is result-for-key1"}
                        """;
            }
        });

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, new AgentTask("Find the answer"));

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
        toolRegistry.register(new SimpleToolDefinition("dangerous", "A dangerous tool", input -> "should not run"));

        when(policyEngine.evaluate(any(), eq("dangerous"), any())).thenReturn(PolicyDecision.DENY);

        ChatClient chatClient = mockChatClient(() ->
                """
                {"thought": "Using dangerous tool", "action": "dangerous", "input": "payload"}
                """);

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, new AgentTask("Do something dangerous"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getResult()).contains("Policy denied");

        var eventTypes = task.getAuditTrail().stream().map(e -> e.type()).toList();
        assertThat(eventTypes).contains("POLICY_CHECK", "POLICY_DENIED");
    }

    @Test
    void requireApproval_autoApprovesAndContinues() {
        toolRegistry.register(new SimpleToolDefinition("sensitive", "Sensitive operation", input -> "done"));

        when(policyEngine.evaluate(any(), eq("sensitive"), any())).thenReturn(PolicyDecision.REQUIRE_APPROVAL);
        when(policyEngine.evaluate(any(), eq("task_complete"), any())).thenReturn(PolicyDecision.ALLOW);

        var callCounter = new AtomicInteger(0);
        ChatClient chatClient = mockChatClient(() -> {
            int call = callCounter.getAndIncrement();
            if (call == 0) {
                return """
                        {"thought": "Running sensitive op", "action": "sensitive", "input": "data"}
                        """;
            } else {
                return """
                        {"thought": "Done", "action": "task_complete", "input": "Operation completed"}
                        """;
            }
        });

        // Uses default 3-arg constructor which delegates to AutoApprovalHandler
        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, new AgentTask("Run sensitive operation"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getResult()).isEqualTo("Operation completed");

        var eventTypes = task.getAuditTrail().stream().map(e -> e.type()).toList();
        assertThat(eventTypes).contains("APPROVAL_REQUESTED", "APPROVAL_GRANTED", "TOOL_EXECUTED");
    }

    @Test
    void approvalRejected_taskCancelled() {
        toolRegistry.register(new SimpleToolDefinition("sensitive", "Sensitive operation", input -> "should not run"));

        when(policyEngine.evaluate(any(), eq("sensitive"), any())).thenReturn(PolicyDecision.REQUIRE_APPROVAL);

        ChatClient chatClient = mockChatClient(() ->
                """
                {"thought": "Running sensitive op", "action": "sensitive", "input": "data"}
                """);

        // Mock handler that rejects
        ApprovalHandler rejectHandler = mock(ApprovalHandler.class);
        when(rejectHandler.handle(any())).thenReturn(ApprovalResult.rejected("Too risky"));

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine, rejectHandler, 10);
        AgentTask task = runtime.execute(agent, new AgentTask("Run sensitive operation"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(task.getResult()).contains("Approval rejected");
        assertThat(task.getResult()).contains("Too risky");

        var eventTypes = task.getAuditTrail().stream().map(e -> e.type()).toList();
        assertThat(eventTypes).contains("APPROVAL_REQUESTED", "APPROVAL_REJECTED");
        assertThat(eventTypes).doesNotContain("TOOL_EXECUTED");
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
        AgentTask task = runtime.execute(agent, new AgentTask("Use unknown tool"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getResult()).contains("Unknown tool");
    }

    @Test
    void taskComplete_executesAsRegularTool() {
        toolRegistry.register(new SimpleToolDefinition("lookup", "Looks up data", input -> "result-for-" + input));
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        var callCounter = new AtomicInteger(0);
        ChatClient chatClient = mockChatClient(() -> {
            int call = callCounter.getAndIncrement();
            if (call == 0) {
                return """
                        {"thought": "Looking up data first", "action": "lookup", "input": "key1"}
                        """;
            } else {
                return """
                        {"thought": "All done", "action": "task_complete", "input": "Looked up key1 and got result-for-key1"}
                        """;
            }
        });

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, new AgentTask("Look up and report"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getResult()).isEqualTo("Looked up key1 and got result-for-key1");

        // task_complete goes through normal tool pipeline — policy is checked
        verify(policyEngine).evaluate(agent, "task_complete", "Looked up key1 and got result-for-key1");

        var eventTypes = task.getAuditTrail().stream().map(e -> e.type()).toList();
        assertThat(eventTypes).contains("TOOL_EXECUTED", "TASK_COMPLETED");
    }

    @Test
    void malformedLlmResponse_failsGracefully() {
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        ChatClient chatClient = mockChatClient(() -> "this is not json at all");

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, new AgentTask("Bad response"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getResult()).contains("Failed to parse");
    }

    @Test
    void execute_withListener_callsAllCallbacks() {
        toolRegistry.register(new SimpleToolDefinition("lookup", "Looks up data", input -> "result-for-" + input));
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        var callCounter = new AtomicInteger(0);
        ChatClient chatClient = mockChatClient(() -> {
            int call = callCounter.getAndIncrement();
            if (call == 0) {
                return """
                        {"thought": "Looking up", "action": "lookup", "input": "key1"}
                        """;
            } else {
                return """
                        {"thought": "Done", "action": "task_complete", "input": "Found it"}
                        """;
            }
        });

        TaskProgressListener listener = mock(TaskProgressListener.class);
        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, new AgentTask("Find something"), listener);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).onTaskStarted(any());
        inOrder.verify(listener).onToolRequested(any(), eq("lookup"), eq("key1"));
        inOrder.verify(listener).onPolicyChecked(any(), eq("lookup"), eq(PolicyDecision.ALLOW));
        inOrder.verify(listener).onToolExecuted(any(), eq("lookup"), eq("result-for-key1"));
        inOrder.verify(listener).onTaskCompleted(any());
    }

    @Test
    void execute_withNullListener_worksNormally() {
        toolRegistry.register(new SimpleToolDefinition("lookup", "Looks up data", input -> "result"));
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        var callCounter = new AtomicInteger(0);
        ChatClient chatClient = mockChatClient(() -> {
            int call = callCounter.getAndIncrement();
            if (call == 0) {
                return """
                        {"thought": "Looking up", "action": "lookup", "input": "key1"}
                        """;
            } else {
                return """
                        {"thought": "Done", "action": "task_complete", "input": "Found it"}
                        """;
            }
        });

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, new AgentTask("Find something"), null);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getResult()).isEqualTo("Found it");
    }

    @Test
    void execute_listenerException_doesNotBreakRuntime() {
        toolRegistry.register(new SimpleToolDefinition("lookup", "Looks up data", input -> "result"));
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        var callCounter = new AtomicInteger(0);
        ChatClient chatClient = mockChatClient(() -> {
            int call = callCounter.getAndIncrement();
            if (call == 0) {
                return """
                        {"thought": "Looking up", "action": "lookup", "input": "key1"}
                        """;
            } else {
                return """
                        {"thought": "Done", "action": "task_complete", "input": "Found it"}
                        """;
            }
        });

        TaskProgressListener listener = mock(TaskProgressListener.class);
        doThrow(new RuntimeException("Listener broke")).when(listener).onTaskStarted(any());

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, new AgentTask("Find something"), listener);

        // Task should still complete despite listener failure
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getResult()).isEqualTo("Found it");
    }

    @Test
    void objectInput_parsedCorrectly() {
        // Tool that echoes its input so we can verify what it received
        toolRegistry.register(new SimpleToolDefinition("lookup", "Looks up data", input -> "got:" + input));
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        var callCounter = new AtomicInteger(0);
        ChatClient chatClient = mockChatClient(() -> {
            int call = callCounter.getAndIncrement();
            if (call == 0) {
                // LLM sends input as a JSON object, not a string
                return """
                        {"thought": "Looking up", "action": "lookup", "input": {"key": "value", "num": 42}}
                        """;
            } else {
                return """
                        {"thought": "Done", "action": "task_complete", "input": "finished"}
                        """;
            }
        });

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, new AgentTask("Test object input"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);

        // Verify the tool received the JSON object as a string, not empty string
        var toolExecutedEvents = task.getAuditTrail().stream()
                .filter(e -> "TOOL_EXECUTED".equals(e.type()))
                .toList();
        assertThat(toolExecutedEvents).anyMatch(e -> e.detail().contains("got:{\"key\":\"value\",\"num\":42}"));
    }

    @Test
    void repeatedToolCall_breaksLoop() {
        toolRegistry.register(new SimpleToolDefinition("lookup", "Looks up data", input -> "result"));
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        var callCounter = new AtomicInteger(0);
        ChatClient chatClient = mockChatClient(() -> {
            int call = callCounter.getAndIncrement();
            if (call <= 2) {
                return """
                        {"thought": "Looking up", "action": "lookup", "input": "same-key"}
                        """;
            } else {
                return """
                        {"thought": "Done", "action": "task_complete", "input": "finished"}
                        """;
            }
        });

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, new AgentTask("Test loop breaking"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        // Tool should only be executed once (first call), subsequent identical calls are skipped
        long toolExecutions = task.getAuditTrail().stream()
                .filter(e -> "TOOL_EXECUTED".equals(e.type()) && e.detail().contains("tool=lookup"))
                .count();
        assertThat(toolExecutions).isEqualTo(1);
    }

    @Test
    void emptyInput_skipsToolExecution() {
        toolRegistry.register(new SimpleToolDefinition("lookup", "Looks up data", input -> "result"));
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        var callCounter = new AtomicInteger(0);
        ChatClient chatClient = mockChatClient(() -> {
            int call = callCounter.getAndIncrement();
            if (call == 0) {
                return """
                        {"thought": "Looking up", "action": "lookup", "input": ""}
                        """;
            } else {
                return """
                        {"thought": "Done", "action": "task_complete", "input": "finished"}
                        """;
            }
        });

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine);
        AgentTask task = runtime.execute(agent, new AgentTask("Test empty input"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        // lookup should never be executed because input was empty
        long lookupExecutions = task.getAuditTrail().stream()
                .filter(e -> "TOOL_EXECUTED".equals(e.type()) && e.detail().contains("tool=lookup"))
                .count();
        assertThat(lookupExecutions).isEqualTo(0);
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
