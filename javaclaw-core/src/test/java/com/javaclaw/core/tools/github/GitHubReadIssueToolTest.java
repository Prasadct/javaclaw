package com.javaclaw.core.tools.github;

import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GitHubReadIssueToolTest {

    private GitHubApiClient client;
    private GitHubReadIssueTool tool;

    @BeforeEach
    void setUp() {
        client = mock(GitHubApiClient.class);
        tool = new GitHubReadIssueTool(client);
    }

    @Test
    void definitionHasCorrectMetadata() {
        ToolDefinition def = tool.definition();
        assertThat(def.name()).isEqualTo("github_read_issue");
        assertThat(def.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(def.description()).contains("issue");
    }

    @Test
    void readsIssueSuccessfully() {
        String apiResponse = """
                {
                    "title": "Bug in login",
                    "state": "open",
                    "labels": [{"name": "bug"}, {"name": "urgent"}],
                    "body": "Login fails when password contains special characters."
                }
                """;
        when(client.get("https://api.github.com/repos/acme/app/issues/42"))
                .thenReturn(apiResponse);

        String result = tool.execute("""
                {"owner": "acme", "repo": "app", "issue_number": 42}
                """);

        assertThat(result).contains("Issue #42: Bug in login");
        assertThat(result).contains("State: open");
        assertThat(result).contains("Labels: bug, urgent");
        assertThat(result).contains("Login fails when password contains special characters.");
    }

    @Test
    void handlesNotFound() {
        when(client.get(anyString()))
                .thenThrow(new GitHubApiException(404, "Not Found", "Not Found"));

        String result = tool.execute("""
                {"owner": "acme", "repo": "app", "issue_number": 999}
                """);

        assertThat(result).contains("Issue not found");
        assertThat(result).contains("acme/app#999");
    }

    @Test
    void handlesOtherApiError() {
        when(client.get(anyString()))
                .thenThrow(new GitHubApiException(500, "Internal Server Error", "Server error"));

        String result = tool.execute("""
                {"owner": "acme", "repo": "app", "issue_number": 1}
                """);

        assertThat(result).contains("GitHub API error");
    }

    @Test
    void handlesMalformedInput() {
        String result = tool.execute("not json");

        assertThat(result).contains("ERROR").contains("Invalid JSON");
    }

    @Test
    void handlesMissingFields() {
        String result = tool.execute("""
                {"owner": "acme"}
                """);

        assertThat(result).contains("ERROR").contains("required");
    }
}
