package com.javaclaw.core.channel;

import com.javaclaw.core.approval.ApprovalRequest;

/**
 * Platform-agnostic message channel for sending messages and approval requests.
 * Implementations adapt to specific platforms (Slack, Discord, etc.).
 */
public interface MessageChannel {

    /**
     * Sends a message to a channel.
     * @return platform-specific message ID for threading/updating
     */
    String sendMessage(String channelId, String text);

    /**
     * Sends a message in a thread.
     * @return platform-specific message ID
     */
    String sendMessageInThread(String channelId, String threadId, String text);

    /**
     * Sends an interactive approval request (e.g., with approve/reject buttons).
     * @return platform-specific message ID
     */
    String sendApprovalRequest(String channelId, String threadId, ApprovalRequest request);

    /**
     * Updates an existing message (e.g., to remove buttons after a decision).
     */
    void updateMessage(String channelId, String messageId, String newText);
}
