package com.javaclaw.core.spring;

import com.javaclaw.core.approval.AsyncApprovalHandler;
import com.javaclaw.core.channel.MessageChannel;
import com.javaclaw.core.channel.slack.SlackAppManager;
import com.javaclaw.core.channel.slack.SlackMessageChannel;
import com.javaclaw.core.model.AgentDefinition;
import com.javaclaw.core.runtime.AgentRuntime;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = JavaclawAutoConfiguration.class)
@EnableConfigurationProperties(JavaclawProperties.class)
@ConditionalOnProperty(prefix = "javaclaw.slack", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "com.slack.api.bolt.App")
public class SlackAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MethodsClient slackMethodsClient(JavaclawProperties props) {
        return Slack.getInstance().methods(props.getSlack().getBotToken());
    }

    @Bean
    @ConditionalOnMissingBean(MessageChannel.class)
    public MessageChannel slackMessageChannel(MethodsClient methodsClient) {
        return new SlackMessageChannel(methodsClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public SlackAppManager slackAppManager(AgentRuntime agentRuntime,
                                           AgentDefinition agentDefinition,
                                           AsyncApprovalHandler approvalHandler,
                                           MessageChannel messageChannel,
                                           JavaclawProperties props) {
        return new SlackAppManager(
                agentRuntime,
                agentDefinition,
                approvalHandler,
                messageChannel,
                props.getSlack().getAppToken(),
                props.getSlack().getBotToken()
        );
    }
}
