package com.javaclaw.core.channel.slack;

import com.javaclaw.core.approval.ApprovalRequest;
import com.javaclaw.core.channel.MessageChannel;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.chat.ChatUpdateRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.ButtonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SlackMessageChannel implements MessageChannel {

    private static final Logger log = LoggerFactory.getLogger(SlackMessageChannel.class);

    private final MethodsClient methodsClient;

    public SlackMessageChannel(MethodsClient methodsClient) {
        this.methodsClient = methodsClient;
    }

    @Override
    public String sendMessage(String channelId, String text) {
        try {
            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                    .channel(channelId)
                    .text(text)
                    .build();
            ChatPostMessageResponse response = methodsClient.chatPostMessage(request);
            if (response.isOk()) {
                return response.getTs();
            }
            log.error("Slack sendMessage failed: {}", response.getError());
        } catch (Exception e) {
            log.error("Slack sendMessage error: {}", e.getMessage(), e);
        }
        return null;
    }

    @Override
    public String sendMessageInThread(String channelId, String threadId, String text) {
        try {
            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                    .channel(channelId)
                    .threadTs(threadId)
                    .text(text)
                    .build();
            ChatPostMessageResponse response = methodsClient.chatPostMessage(request);
            if (response.isOk()) {
                return response.getTs();
            }
            log.error("Slack sendMessageInThread failed: {}", response.getError());
        } catch (Exception e) {
            log.error("Slack sendMessageInThread error: {}", e.getMessage(), e);
        }
        return null;
    }

    @Override
    public String sendApprovalRequest(String channelId, String threadId, ApprovalRequest request) {
        try {
            String markdownText = String.format(
                    "*Approval Required*\n" +
                    ">*Tool:* `%s`\n" +
                    ">*Risk Level:* %s\n" +
                    ">*Input:* `%s`\n" +
                    ">*Reason:* %s",
                    request.toolName(),
                    request.riskLevel(),
                    truncate(request.toolInput(), 200),
                    request.reason()
            );

            SectionBlock section = SectionBlock.builder()
                    .text(MarkdownTextObject.builder().text(markdownText).build())
                    .build();

            ButtonElement approveButton = ButtonElement.builder()
                    .actionId("approve_tool")
                    .text(PlainTextObject.builder().text("Approve").build())
                    .value(request.taskId())
                    .style("primary")
                    .build();

            ButtonElement rejectButton = ButtonElement.builder()
                    .actionId("reject_tool")
                    .text(PlainTextObject.builder().text("Reject").build())
                    .value(request.taskId())
                    .style("danger")
                    .build();

            ActionsBlock actions = ActionsBlock.builder()
                    .elements(List.of(approveButton, rejectButton))
                    .build();

            String fallbackText = "Approval required for tool '" + request.toolName() + "'";

            ChatPostMessageRequest postRequest = ChatPostMessageRequest.builder()
                    .channel(channelId)
                    .threadTs(threadId)
                    .text(fallbackText)
                    .blocks(List.of(section, actions))
                    .build();
            ChatPostMessageResponse response = methodsClient.chatPostMessage(postRequest);

            if (response.isOk()) {
                return response.getTs();
            }
            log.error("Slack sendApprovalRequest failed: {}", response.getError());
        } catch (Exception e) {
            log.error("Slack sendApprovalRequest error: {}", e.getMessage(), e);
        }
        return null;
    }

    @Override
    public void updateMessage(String channelId, String messageId, String newText) {
        try {
            ChatUpdateRequest request = ChatUpdateRequest.builder()
                    .channel(channelId)
                    .ts(messageId)
                    .text(newText)
                    .blocks(List.of())
                    .build();
            ChatUpdateResponse response = methodsClient.chatUpdate(request);
            if (!response.isOk()) {
                log.error("Slack updateMessage failed: {}", response.getError());
            }
        } catch (Exception e) {
            log.error("Slack updateMessage error: {}", e.getMessage(), e);
        }
    }

    static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
