package com.javaclaw.core.channel.slack;

import com.javaclaw.core.approval.ApprovalResult;
import com.javaclaw.core.approval.AsyncApprovalHandler;
import com.javaclaw.core.channel.MessageChannel;
import com.javaclaw.core.model.AgentDefinition;
import com.javaclaw.core.model.AgentTask;
import com.javaclaw.core.runtime.AgentRuntime;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SlackAppManager {

    private static final Logger log = LoggerFactory.getLogger(SlackAppManager.class);

    private final AgentRuntime agentRuntime;
    private final AgentDefinition agentDefinition;
    private final AsyncApprovalHandler approvalHandler;
    private final MessageChannel messageChannel;
    private final String appToken;
    private final String botToken;

    private SocketModeApp socketModeApp;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "javaclaw-slack-worker");
        t.setDaemon(true);
        return t;
    });

    public SlackAppManager(AgentRuntime agentRuntime,
                           AgentDefinition agentDefinition,
                           AsyncApprovalHandler approvalHandler,
                           MessageChannel messageChannel,
                           String appToken,
                           String botToken) {
        this.agentRuntime = agentRuntime;
        this.agentDefinition = agentDefinition;
        this.approvalHandler = approvalHandler;
        this.messageChannel = messageChannel;
        this.appToken = appToken;
        this.botToken = botToken;
    }

    @PostConstruct
    public void start() throws Exception {
        AppConfig appConfig = new AppConfig();
        appConfig.setSingleTeamBotToken(botToken);
        App app = new App(appConfig);

        // Handle @mentions in channels
        app.event(com.slack.api.model.event.AppMentionEvent.class, (payload, ctx) -> {
            var event = payload.getEvent();
            String text = event.getText().replaceAll("<@[A-Z0-9]+>", "").trim();
            String channelId = event.getChannel();
            String threadTs = event.getThreadTs() != null ? event.getThreadTs() : event.getTs();
            handleIncomingMessage(channelId, threadTs, text);
            return ctx.ack();
        });

        // Handle direct messages
        app.event(com.slack.api.model.event.MessageEvent.class, (payload, ctx) -> {
            var event = payload.getEvent();
            if ("im".equals(event.getChannelType()) && event.getBotId() == null) {
                String channelId = event.getChannel();
                String threadTs = event.getThreadTs() != null ? event.getThreadTs() : event.getTs();
                handleIncomingMessage(channelId, threadTs, event.getText());
            }
            return ctx.ack();
        });

        // Handle approve button click
        app.blockAction("approve_tool", (req, ctx) -> {
            String taskId = req.getPayload().getActions().get(0).getValue();
            log.info("Approval granted via Slack for task: {}", taskId);
            approvalHandler.submitDecision(taskId, ApprovalResult.approved("Approved via Slack"));

            // Update the message to remove buttons
            String channelId = req.getPayload().getChannel().getId();
            String messageTs = req.getPayload().getMessage().getTs();
            messageChannel.updateMessage(channelId, messageTs, "Approved by " + req.getPayload().getUser().getUsername());

            return ctx.ack();
        });

        // Handle reject button click
        app.blockAction("reject_tool", (req, ctx) -> {
            String taskId = req.getPayload().getActions().get(0).getValue();
            log.info("Approval rejected via Slack for task: {}", taskId);
            approvalHandler.submitDecision(taskId, ApprovalResult.rejected("Rejected via Slack"));

            // Update the message to remove buttons
            String channelId = req.getPayload().getChannel().getId();
            String messageTs = req.getPayload().getMessage().getTs();
            messageChannel.updateMessage(channelId, messageTs, "Rejected by " + req.getPayload().getUser().getUsername());

            return ctx.ack();
        });

        socketModeApp = new SocketModeApp(appToken, app);
        socketModeApp.startAsync();
        log.info("Slack bot started in Socket Mode");
    }

    private void handleIncomingMessage(String channelId, String threadTs, String goal) {
        if (goal == null || goal.isBlank()) {
            messageChannel.sendMessageInThread(channelId, threadTs, "Please provide a goal or question.");
            return;
        }

        AgentTask task = new AgentTask(goal);
        SlackTaskProgressListener listener = new SlackTaskProgressListener(messageChannel, channelId, threadTs);

        executor.submit(() -> {
            try {
                agentRuntime.execute(agentDefinition, task, listener);
            } catch (Exception e) {
                log.error("Agent execution failed for Slack message: {}", e.getMessage(), e);
                messageChannel.sendMessageInThread(channelId, threadTs, "Agent execution failed: " + e.getMessage());
            }
        });
    }

    @PreDestroy
    public void stop() throws Exception {
        if (socketModeApp != null) {
            socketModeApp.close();
            log.info("Slack bot stopped");
        }
        executor.shutdown();
    }
}
