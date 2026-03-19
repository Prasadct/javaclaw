package com.javaclaw.demo;

import com.javaclaw.core.approval.ApprovalHandler;
import com.javaclaw.core.approval.AsyncApprovalHandler;
import com.javaclaw.core.model.AgentDefinition;
import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.model.ToolDefinition;
import com.javaclaw.core.policy.PolicyDecision;
import com.javaclaw.core.policy.PolicyEngine;
import com.javaclaw.core.spring.JavaclawProperties;
import com.javaclaw.core.tools.ReadFileTool;
import com.javaclaw.core.tools.SearchCodeTool;
import com.javaclaw.core.tools.ShellCommandTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

@Configuration
public class DemoAgentConfig {

    @Bean
    public ToolDefinition readFileTool(JavaclawProperties properties) {
        Path base = Path.of(properties.getTools().getBaseDirectory());
        return new ReadFileTool(base).definition();
    }

    @Bean
    public ToolDefinition searchCodeTool(JavaclawProperties properties) {
        Path base = Path.of(properties.getTools().getBaseDirectory());
        return new SearchCodeTool(base).definition();
    }

    @Bean
    public ToolDefinition shellCommandTool(JavaclawProperties properties) {
        Path base = Path.of(properties.getTools().getBaseDirectory());
        return new ShellCommandTool(base).definition();
    }

    @Bean
    public AgentDefinition bugfixAssistant() {
        return new AgentDefinition(
                "bugfix-assistant",
                """
                You are a senior Java developer. You help find and fix bugs in Java projects.
                When given a bug description, you:
                1. Search the codebase to find relevant files
                2. Read the relevant files to understand the code
                3. Identify the bug
                4. Explain the fix clearly
                Always think step by step. Use tools to gather information before suggesting fixes.""",
                List.of("read_file", "search_code", "run_command")
        );
    }

    @Bean
    public PolicyEngine policyEngine() {
        return (agent, toolName, input) -> {
            // HIGH risk tools require approval
            if ("run_command".equals(toolName)) {
                return PolicyDecision.REQUIRE_APPROVAL;
            }
            // Check if the tool is in the agent's allowed list
            if (!agent.allowedTools().contains(toolName)) {
                return PolicyDecision.DENY;
            }
            return PolicyDecision.ALLOW;
        };
    }

    @Bean
    public AsyncApprovalHandler asyncApprovalHandler(JavaclawProperties properties) {
        return new AsyncApprovalHandler(properties.getApproval().getTimeoutSeconds());
    }

    @Bean
    public ApprovalHandler approvalHandler(AsyncApprovalHandler asyncApprovalHandler) {
        return asyncApprovalHandler;
    }
}
