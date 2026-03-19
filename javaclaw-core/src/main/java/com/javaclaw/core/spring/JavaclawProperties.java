package com.javaclaw.core.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "javaclaw")
public class JavaclawProperties {

    private ToolsProperties tools = new ToolsProperties();
    private int maxSteps = 10;
    private ApprovalProperties approval = new ApprovalProperties();

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
}
