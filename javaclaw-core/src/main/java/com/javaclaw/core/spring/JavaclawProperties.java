package com.javaclaw.core.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "javaclaw")
public class JavaclawProperties {

    private ToolsProperties tools = new ToolsProperties();
    private int maxSteps = 10;
    private ApprovalProperties approval = new ApprovalProperties();
    private SlackProperties slack = new SlackProperties();

    public ToolsProperties getTools() {
        return tools;
    }

    public void setTools(ToolsProperties tools) {
        this.tools = tools;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public ApprovalProperties getApproval() {
        return approval;
    }

    public void setApproval(ApprovalProperties approval) {
        this.approval = approval;
    }

    public SlackProperties getSlack() {
        return slack;
    }

    public void setSlack(SlackProperties slack) {
        this.slack = slack;
    }

    public static class ToolsProperties {
        private String baseDirectory = ".";

        public String getBaseDirectory() {
            return baseDirectory;
        }

        public void setBaseDirectory(String baseDirectory) {
            this.baseDirectory = baseDirectory;
        }
    }

    public static class ApprovalProperties {
        private int timeoutSeconds = 300;

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class SlackProperties {
        private boolean enabled = false;
        private String botToken;
        private String appToken;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String getAppToken() {
            return appToken;
        }

        public void setAppToken(String appToken) {
            this.appToken = appToken;
        }
    }
}
