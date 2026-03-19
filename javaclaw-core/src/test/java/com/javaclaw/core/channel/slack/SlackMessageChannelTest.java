package com.javaclaw.core.channel.slack;

import com.javaclaw.core.approval.ApprovalRequest;
import com.javaclaw.core.model.RiskLevel;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.chat.ChatUpdateRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.ButtonElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SlackMessageChannelTest {

    private MethodsClient methodsClient;
    private SlackMessageChannel channel;

    @BeforeEach
    void setUp() {
        methodsClient = mock(MethodsClient.class);
        channel = new SlackMessageChannel(methodsClient);
    }

    @Test
    void sendMessage_callsChatPostMessage() throws Exception {
        var response = new ChatPostMessageResponse();
        response.setOk(true);
        response.setTs("1234567890.123456");
        when(methodsClient.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(response);

        String ts = channel.sendMessage("C123", "Hello");

        assertThat(ts).isEqualTo("1234567890.123456");
        verify(methodsClient).chatPostMessage(any(ChatPostMessageRequest.class));
    }

    @Test
    void sendMessageInThread_setsThreadTs() throws Exception {
        var response = new ChatPostMessageResponse();
        response.setOk(true);
        response.setTs("1234567890.654321");
        when(methodsClient.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(response);

        String ts = channel.sendMessageInThread("C123", "thread-ts", "Reply");

        assertThat(ts).isEqualTo("1234567890.654321");
        verify(methodsClient).chatPostMessage(any(ChatPostMessageRequest.class));
    }

    @Test
    void sendApprovalRequest_buildsBlockKit() throws Exception {
        var response = new ChatPostMessageResponse();
        response.setOk(true);
        response.setTs("approval-ts");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ChatPostMessageRequest> captor = ArgumentCaptor.forClass(ChatPostMessageRequest.class);
        when(methodsClient.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(response);

        var request = new ApprovalRequest("task-1", "Fix bug", "run_command", "rm -rf /tmp/test",
                RiskLevel.HIGH, "Policy requires approval");

        String ts = channel.sendApprovalRequest("C123", "thread-ts", request);

        assertThat(ts).isEqualTo("approval-ts");
        verify(methodsClient).chatPostMessage(captor.capture());

        ChatPostMessageRequest captured = captor.getValue();
        assertThat(captured.getBlocks()).hasSize(2);

        // Verify actions block has approve and reject buttons
        LayoutBlock actionsBlock = captured.getBlocks().get(1);
        assertThat(actionsBlock).isInstanceOf(ActionsBlock.class);
        ActionsBlock actions = (ActionsBlock) actionsBlock;
        assertThat(actions.getElements()).hasSize(2);

        ButtonElement approveButton = (ButtonElement) actions.getElements().get(0);
        assertThat(approveButton.getActionId()).isEqualTo("approve_tool");
        assertThat(approveButton.getValue()).isEqualTo("task-1");

        ButtonElement rejectButton = (ButtonElement) actions.getElements().get(1);
        assertThat(rejectButton.getActionId()).isEqualTo("reject_tool");
        assertThat(rejectButton.getValue()).isEqualTo("task-1");
    }

    @Test
    void updateMessage_callsChatUpdate() throws Exception {
        var response = new ChatUpdateResponse();
        response.setOk(true);
        when(methodsClient.chatUpdate(any(ChatUpdateRequest.class))).thenReturn(response);

        channel.updateMessage("C123", "msg-ts", "Updated text");

        verify(methodsClient).chatUpdate(any(ChatUpdateRequest.class));
    }

    @Test
    void sendMessage_apiError_returnsNull() throws Exception {
        var response = new ChatPostMessageResponse();
        response.setOk(false);
        response.setError("channel_not_found");
        when(methodsClient.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(response);

        String ts = channel.sendMessage("C123", "Hello");

        assertThat(ts).isNull();
    }

    @Test
    void sendMessage_exception_returnsNull() throws Exception {
        when(methodsClient.chatPostMessage(any(ChatPostMessageRequest.class)))
                .thenThrow(new RuntimeException("Connection failed"));

        String ts = channel.sendMessage("C123", "Hello");

        assertThat(ts).isNull();
    }

    @Test
    void truncate_shortText_unchanged() {
        assertThat(SlackMessageChannel.truncate("short", 10)).isEqualTo("short");
    }

    @Test
    void truncate_longText_truncated() {
        assertThat(SlackMessageChannel.truncate("a".repeat(300), 200)).hasSize(203).endsWith("...");
    }

    @Test
    void truncate_null_returnsEmpty() {
        assertThat(SlackMessageChannel.truncate(null, 200)).isEmpty();
    }
}
