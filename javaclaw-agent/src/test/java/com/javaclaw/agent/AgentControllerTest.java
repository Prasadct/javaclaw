package com.javaclaw.agent;

import com.javaclaw.core.approval.AsyncApprovalHandler;
import com.javaclaw.core.model.AgentDefinition;
import com.javaclaw.core.model.SimpleToolDefinition;
import com.javaclaw.core.policy.PolicyDecision;
import com.javaclaw.core.policy.PolicyEngine;
import com.javaclaw.core.runtime.AgentRuntime;
import com.javaclaw.core.runtime.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentControllerTest {

    private AgentController controller;
    private AsyncApprovalHandler asyncApprovalHandler;
    private ToolRegistry toolRegistry;
    private PolicyEngine policyEngine;
    private AgentDefinition agent;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        policyEngine = mock(PolicyEngine.class);
        asyncApprovalHandler = new AsyncApprovalHandler(30);
        toolRegistry.register(new SimpleToolDefinition("task_complete", "Complete the task", input -> input));
        agent = new AgentDefinition("test-agent", "You are a test agent.",
                List.of("lookup", "sensitive", "task_complete"));
    }

    @Test
    void startTask_returnsSubmissionId() {
        ChatClient chatClient = mockChatClient(() ->
                """
                {"thought": "Done", "action": "task_complete", "input": "result"}
                """);
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(PolicyDecision.ALLOW);

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine, asyncApprovalHandler, 10);
        controller = new AgentController(runtime, agent, asyncApprovalHandler);

        Map<String, Object> response = controller.startTask(Map.of("goal", "Test goal"));
        assertThat(response).containsKey("taskId");
        assertThat(response.get("goal")).isEqualTo("Test goal");
        assertThat(response.get("status")).isEqualTo("CREATED");
    }

    @Test
    void asyncApprovalFlow_approveViaEndpoint() throws Exception {
        toolRegistry.register(new SimpleToolDefinition("sensitive", "Sensitive op", input -> "executed"));

        when(policyEngine.evaluate(any(), eq("sensitive"), any())).thenReturn(PolicyDecision.REQUIRE_APPROVAL);
        when(policyEngine.evaluate(any(), eq("task_complete"), any())).thenReturn(PolicyDecision.ALLOW);

        var callCounter = new AtomicInteger(0);
        ChatClient chatClient = mockChatClient(() -> {
            int call = callCounter.getAndIncrement();
            if (call == 0) {
                return """
                        {"thought": "Running sensitive", "action": "sensitive", "input": "data"}
                        """;
            } else {
                return """
                        {"thought": "Done", "action": "task_complete", "input": "Completed after approval"}
                        """;
            }
        });

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine, asyncApprovalHandler, 10);
        controller = new AgentController(runtime, agent, asyncApprovalHandler);

        // Start the task — taskId is returned immediately
        Map<String, Object> startResponse = controller.startTask(Map.of("goal", "Sensitive operation"));
        String taskId = (String) startResponse.get("taskId");
        assertThat(taskId).isNotNull();

        // Wait for pending approval to appear
        long deadline = System.currentTimeMillis() + 5000;
        while (asyncApprovalHandler.getPendingApprovals().isEmpty()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("No pending approval appeared within timeout");
            }
            Thread.sleep(50);
        }

        // Verify pending approval is visible
        Map<String, Object> pending = controller.getPendingApprovals();
        assertThat((int) pending.get("count")).isEqualTo(1);

        // Approve it using the taskId from the start response
        Map<String, Object> approveResponse = controller.approveTask(taskId,
                Map.of("approved", true, "reason", "Looks safe"));
        assertThat(approveResponse.get("submitted")).isEqualTo(true);
        assertThat(approveResponse.get("approved")).isEqualTo(true);

        // Wait for task to complete
        deadline = System.currentTimeMillis() + 5000;
        Map<String, Object> taskResponse = null;
        while (System.currentTimeMillis() < deadline) {
            taskResponse = controller.getTask(taskId);
            if ("COMPLETED".equals(taskResponse.get("status"))) break;
            Thread.sleep(50);
        }

        assertThat(taskResponse).isNotNull();
        assertThat(taskResponse.get("status")).isEqualTo("COMPLETED");
        assertThat(taskResponse.get("result")).isEqualTo("Completed after approval");
    }

    @Test
    void asyncApprovalFlow_rejectViEndpoint() throws Exception {
        toolRegistry.register(new SimpleToolDefinition("sensitive", "Sensitive op", input -> "should not run"));

        when(policyEngine.evaluate(any(), eq("sensitive"), any())).thenReturn(PolicyDecision.REQUIRE_APPROVAL);

        ChatClient chatClient = mockChatClient(() ->
                """
                {"thought": "Running sensitive", "action": "sensitive", "input": "data"}
                """);

        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine, asyncApprovalHandler, 10);
        controller = new AgentController(runtime, agent, asyncApprovalHandler);

        // Start the task — taskId is returned immediately
        Map<String, Object> startResponse = controller.startTask(Map.of("goal", "Sensitive operation"));
        String taskId = (String) startResponse.get("taskId");

        // Wait for pending approval
        long deadline = System.currentTimeMillis() + 5000;
        while (asyncApprovalHandler.getPendingApprovals().isEmpty()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("No pending approval appeared within timeout");
            }
            Thread.sleep(50);
        }

        // Reject it using the taskId from the start response
        controller.approveTask(taskId, Map.of("approved", false, "reason", "Too dangerous"));

        // Wait for task to finish
        deadline = System.currentTimeMillis() + 5000;
        Map<String, Object> taskResponse = null;
        while (System.currentTimeMillis() < deadline) {
            taskResponse = controller.getTask(taskId);
            if ("CANCELLED".equals(taskResponse.get("status"))) break;
            Thread.sleep(50);
        }

        assertThat(taskResponse).isNotNull();
        assertThat(taskResponse.get("status")).isEqualTo("CANCELLED");
        assertThat((String) taskResponse.get("result")).contains("Too dangerous");
    }

    @Test
    void getPendingApprovals_emptyByDefault() {
        ChatClient chatClient = mockChatClient(() -> """
                {"thought": "Done", "action": "task_complete", "input": "result"}
                """);
        var runtime = new AgentRuntime(chatClient, toolRegistry, policyEngine, asyncApprovalHandler, 10);
        controller = new AgentController(runtime, agent, asyncApprovalHandler);

        Map<String, Object> pending = controller.getPendingApprovals();
        assertThat((int) pending.get("count")).isEqualTo(0);
    }

    @FunctionalInterface
    interface ResponseSupplier {
        String get();
    }

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
