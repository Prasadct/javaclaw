package com.javaclaw.demo;

import com.javaclaw.core.model.AgentDefinition;
import com.javaclaw.core.model.AgentTask;
import com.javaclaw.core.runtime.AgentRuntime;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentRuntime agentRuntime;
    private final AgentDefinition agentDefinition;

    public AgentController(AgentRuntime agentRuntime, AgentDefinition agentDefinition) {
        this.agentRuntime = agentRuntime;
        this.agentDefinition = agentDefinition;
    }

    @PostMapping("/task")
    public AgentTask executeTask(@RequestBody Map<String, String> request) {
        String goal = request.get("goal");
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("'goal' field is required");
        }
        return agentRuntime.execute(agentDefinition, goal);
    }
}
