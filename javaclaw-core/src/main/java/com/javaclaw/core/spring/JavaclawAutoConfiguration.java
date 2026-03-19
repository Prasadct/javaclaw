package com.javaclaw.core.spring;

import com.javaclaw.core.approval.ApprovalHandler;
import com.javaclaw.core.approval.CLIApprovalHandler;
import com.javaclaw.core.model.AgentDefinition;
import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.model.ToolDefinition;
import com.javaclaw.core.policy.PolicyDecision;
import com.javaclaw.core.policy.PolicyEngine;
import com.javaclaw.core.runtime.AgentRuntime;
import com.javaclaw.core.runtime.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(JavaclawProperties.class)
public class JavaclawAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JavaclawAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public PolicyEngine policyEngine() {
        return (AgentDefinition agent, String toolName, String input) -> {
            log.debug("Default policy engine evaluating tool '{}' for agent '{}'", toolName, agent.name());
            return PolicyDecision.ALLOW;
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(List<ToolDefinition> toolDefinitions) {
        var registry = new ToolRegistry();
        for (var tool : toolDefinitions) {
            registry.register(tool);
            log.info("Auto-registered tool: {} (risk={})", tool.name(), tool.riskLevel());
        }
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean(ApprovalHandler.class)
    public ApprovalHandler approvalHandler(JavaclawProperties properties) {
        return new CLIApprovalHandler(properties.getApproval().getTimeoutSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentRuntime agentRuntime(ChatClient.Builder chatClientBuilder,
                                     ToolRegistry toolRegistry,
                                     PolicyEngine policyEngine,
                                     ApprovalHandler approvalHandler,
                                     JavaclawProperties properties) {
        ChatClient chatClient = chatClientBuilder.build();
        return new AgentRuntime(chatClient, toolRegistry, policyEngine, approvalHandler, properties.getMaxSteps());
    }
}
