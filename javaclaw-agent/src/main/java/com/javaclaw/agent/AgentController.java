package com.javaclaw.agent;

import com.javaclaw.core.approval.ApprovalRequest;
import com.javaclaw.core.approval.ApprovalResult;
import com.javaclaw.core.approval.AsyncApprovalHandler;
import com.javaclaw.core.model.AgentDefinition;
import com.javaclaw.core.model.AgentTask;
import com.javaclaw.core.model.TaskStatus;
import com.javaclaw.core.runtime.AgentRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentRuntime agentRuntime;
    private final AgentDefinition agentDefinition;
    private final AsyncApprovalHandler asyncApprovalHandler;
    private final ConcurrentHashMap<String, AgentTask> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-task");
        t.setDaemon(true);
        return t;
    });

    public AgentController(AgentRuntime agentRuntime,
                           AgentDefinition agentDefinition,
                           AsyncApprovalHandler asyncApprovalHandler) {
        this.agentRuntime = agentRuntime;
        this.agentDefinition = agentDefinition;
        this.asyncApprovalHandler = asyncApprovalHandler;
    }

    @PostMapping("/task")
    public Map<String, Object> startTask(@RequestBody Map<String, String> request) {
        String goal = request.get("goal");
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("'goal' field is required");
        }

        AgentTask task = new AgentTask(goal);
        tasks.put(task.getId(), task);

        String initialStatus = task.getStatus().name();

        executor.submit(() -> {
            try {
                agentRuntime.execute(agentDefinition, task);
                log.info("Task {} finished with status {}", task.getId(), task.getStatus());
            } catch (Exception e) {
                log.error("Task execution failed for task {}: {}", task.getId(), e.getMessage(), e);
            }
        });

        return Map.of(
                "taskId", task.getId(),
                "status", initialStatus,
                "goal", goal
        );
    }

    @GetMapping("/task/{taskId}")
    public Map<String, Object> getTask(@PathVariable String taskId) {
        AgentTask task = tasks.get(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + taskId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.getId());
        result.put("goal", task.getGoal());
        result.put("status", task.getStatus().name());
        result.put("result", task.getResult());
        result.put("auditTrail", task.getAuditTrail());
        if (task.getStatus() == TaskStatus.WAITING_FOR_APPROVAL) {
            var approval = asyncApprovalHandler.getPendingApprovals().get(taskId);
            if (approval != null) {
                result.put("pendingApproval", Map.of(
                        "toolName", approval.toolName(),
                        "toolInput", approval.toolInput(),
                        "riskLevel", approval.riskLevel().name(),
                        "reason", approval.reason()
                ));
            }
        }
        return result;
    }

    @GetMapping("/tasks")
    public Map<String, Object> getAllTasks() {
        Map<String, Object> summaries = new LinkedHashMap<>();
        for (var entry : tasks.entrySet()) {
            AgentTask task = entry.getValue();
            summaries.put(entry.getKey(), Map.of(
                    "goal", task.getGoal(),
                    "status", task.getStatus().name(),
                    "result", task.getResult() != null ? task.getResult() : ""
            ));
        }
        return Map.of("count", tasks.size(), "tasks", summaries);
    }

    @PostMapping("/task/{taskId}/approve")
    public Map<String, Object> approveTask(@PathVariable String taskId,
                                           @RequestBody Map<String, Object> request) {
        Boolean approved = (Boolean) request.get("approved");
        if (approved == null) {
            throw new IllegalArgumentException("'approved' field is required");
        }
        String reason = (String) request.getOrDefault("reason", approved ? "Approved via API" : "Rejected via API");

        ApprovalResult result = approved
                ? ApprovalResult.approved(reason)
                : ApprovalResult.rejected(reason);

        boolean submitted = asyncApprovalHandler.submitDecision(taskId, result);
        if (!submitted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No pending approval found for task: " + taskId);
        }
        return Map.of(
                "taskId", taskId,
                "submitted", submitted,
                "approved", approved
        );
    }

    @GetMapping("/approvals/pending")
    public Map<String, Object> getPendingApprovals() {
        var pending = asyncApprovalHandler.getPendingApprovals();
        Map<String, Object> formatted = new LinkedHashMap<>();
        for (var entry : pending.entrySet()) {
            ApprovalRequest req = entry.getValue();
            formatted.put(entry.getKey(), Map.of(
                    "taskId", req.taskId(),
                    "goal", req.goal(),
                    "toolName", req.toolName(),
                    "toolInput", req.toolInput(),
                    "riskLevel", req.riskLevel().name(),
                    "reason", req.reason()
            ));
        }
        return Map.of("count", pending.size(), "approvals", formatted);
    }
}
