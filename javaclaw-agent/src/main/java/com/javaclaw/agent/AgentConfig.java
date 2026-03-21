package com.javaclaw.agent;

import com.javaclaw.core.approval.ApprovalHandler;
import com.javaclaw.core.approval.AsyncApprovalHandler;
import com.javaclaw.core.model.AgentDefinition;
import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.model.SimpleToolDefinition;
import com.javaclaw.core.model.ToolDefinition;
import com.javaclaw.core.policy.PolicyDecision;
import com.javaclaw.core.policy.PolicyEngine;
import com.javaclaw.core.spring.JavaclawProperties;
import com.javaclaw.core.tools.ReadFileTool;
import com.javaclaw.core.tools.SearchCodeTool;
import com.javaclaw.core.tools.ShellCommandTool;
import com.javaclaw.core.tools.github.GitHubCreatePRTool;
import com.javaclaw.core.tools.github.GitHubReadFileTool;
import com.javaclaw.core.tools.github.GitHubReadIssueTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

@Configuration
public class AgentConfig {

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
    @ConditionalOnProperty(name = "javaclaw.github.token")
    public ToolDefinition githubReadIssueTool(@Value("${javaclaw.github.token}") String token) {
        return new GitHubReadIssueTool(token).definition();
    }

    @Bean
    @ConditionalOnProperty(name = "javaclaw.github.token")
    public ToolDefinition githubReadFileTool(@Value("${javaclaw.github.token}") String token) {
        return new GitHubReadFileTool(token).definition();
    }

    @Bean
    @ConditionalOnProperty(name = "javaclaw.github.token")
    public ToolDefinition githubCreatePRTool(@Value("${javaclaw.github.token}") String token) {
        return new GitHubCreatePRTool(token);
    }

    @Bean
    public ToolDefinition taskCompleteTool() {
        return new SimpleToolDefinition(
                "task_complete",
                "Signal that you have completed ALL actions the user requested. " +
                "Input: a summary of what you accomplished. " +
                "Only call this after you have performed all requested actions using the tools above.",
                input -> input
        );
    }

    @Bean
    public AgentDefinition bugfixAssistant() {
        return new AgentDefinition(
                "bugfix-assistant",
                """
                You are a senior Java developer. You help find and fix bugs in Java projects.

                When working with a GitHub repository:
                1. Use github_read_issue to read the bug report
                2. Use github_read_file to read source files (guess common paths like src/main/java/... based on class names in the issue)
                3. Identify the bug and determine the fix
                4. If asked to create a PR, use github_create_pr to create a pull request with the fixed code
                5. Call task_complete with a summary of what you did

                Important:
                - search_code and read_file only work on LOCAL files, NOT on GitHub repositories.
                - For GitHub repos, use github_read_file to read files. If you don't know the exact path, try common Java paths like src/main/java/<ClassName>.java.
                - If a tool returns an error, try a different approach — do not repeat the same call.
                - Always think step by step. Follow the user's instructions carefully.""",
                List.of("read_file", "search_code", "run_command", "github_read_issue", "github_read_file", "github_create_pr", "task_complete")
        );
    }

    @Bean
    public PolicyEngine policyEngine(List<ToolDefinition> allTools) {
        // Build a set of HIGH risk tool names for policy checks
        var highRiskTools = allTools.stream()
                .filter(t -> t.riskLevel() == RiskLevel.HIGH)
                .map(ToolDefinition::name)
                .collect(java.util.stream.Collectors.toSet());

        return (agent, toolName, input) -> {
            // HIGH risk tools require approval
            if (highRiskTools.contains(toolName)) {
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
