package com.javaclaw.core.tools.github;

import com.javaclaw.core.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GitHubCreatePRToolTest {

    private GitHubApiClient client;
    private GitHubCreatePRTool tool;

    @BeforeEach
    void setUp() {
        client = mock(GitHubApiClient.class);
        tool = new GitHubCreatePRTool(client);
    }

    @Test
    void hasCorrectMetadata() {
        assertThat(tool.name()).isEqualTo("github_create_pr");
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(tool.description()).contains("pull request");
    }

    @Test
    void execute_happyPath_createsPR() {
        // Step 1: Get base branch SHA
        when(client.get(contains("/git/ref/heads/main")))
                .thenReturn("{\"object\":{\"sha\":\"abc123\"}}");

        // Step 2: Create branch
        when(client.post(contains("/git/refs"), anyString()))
                .thenReturn("{\"ref\":\"refs/heads/fix-branch\"}");

        // Step 3: Get file SHA
        when(client.get(contains("/contents/src/Main.java?ref=fix-branch")))
                .thenReturn("{\"sha\":\"file-sha-456\",\"content\":\"\"}");

        // Step 4: Update file
        when(client.put(contains("/contents/src/Main.java"), anyString()))
                .thenReturn("{\"content\":{\"sha\":\"new-sha\"}}");

        // Step 5: Create PR
        when(client.post(contains("/pulls"), anyString()))
                .thenReturn("{\"html_url\":\"https://github.com/owner/repo/pull/42\"}");

        String input = """
                {"owner":"owner","repo":"repo","base_branch":"main","branch_name":"fix-branch",
                 "file_path":"src/Main.java","new_file_content":"fixed code",
                 "pr_title":"Fix bug","pr_body":"Fixes #1","commit_message":"fix: resolve bug"}
                """;

        String result = tool.execute(input);

        assertThat(result).contains("https://github.com/owner/repo/pull/42");
        verify(client).get(contains("/git/ref/heads/main"));
        verify(client).post(contains("/git/refs"), anyString());
        verify(client).put(contains("/contents/src/Main.java"), anyString());
        verify(client).post(contains("/pulls"), anyString());
    }

    @Test
    void execute_branchAlreadyExists_continuesSuccessfully() {
        when(client.get(contains("/git/ref/heads/main")))
                .thenReturn("{\"object\":{\"sha\":\"abc123\"}}");

        // Branch creation returns 422
        when(client.post(contains("/git/refs"), anyString()))
                .thenThrow(new GitHubApiException(422, "Reference already exists", "POST request failed"));

        when(client.get(contains("/contents/src/Main.java?ref=fix-branch")))
                .thenReturn("{\"sha\":\"file-sha-456\",\"content\":\"\"}");

        when(client.put(contains("/contents/src/Main.java"), anyString()))
                .thenReturn("{\"content\":{\"sha\":\"new-sha\"}}");

        when(client.post(contains("/pulls"), anyString()))
                .thenReturn("{\"html_url\":\"https://github.com/owner/repo/pull/43\"}");

        String input = """
                {"owner":"owner","repo":"repo","base_branch":"main","branch_name":"fix-branch",
                 "file_path":"src/Main.java","new_file_content":"fixed code",
                 "pr_title":"Fix bug","pr_body":"","commit_message":"fix: resolve bug"}
                """;

        String result = tool.execute(input);

        assertThat(result).contains("pull/43");
    }

    @Test
    void execute_apiFailureOnCreatePR_returnsErrorString() {
        when(client.get(contains("/git/ref/heads/main")))
                .thenReturn("{\"object\":{\"sha\":\"abc123\"}}");
        when(client.post(contains("/git/refs"), anyString()))
                .thenReturn("{\"ref\":\"refs/heads/fix-branch\"}");
        when(client.get(contains("/contents/")))
                .thenThrow(new GitHubApiException(404, "Not found", "GET request failed"));
        when(client.put(contains("/contents/"), anyString()))
                .thenReturn("{\"content\":{\"sha\":\"new-sha\"}}");
        when(client.post(contains("/pulls"), anyString()))
                .thenThrow(new GitHubApiException(500, "Internal Server Error", "POST request failed"));

        String input = """
                {"owner":"owner","repo":"repo","base_branch":"main","branch_name":"fix-branch",
                 "file_path":"src/New.java","new_file_content":"new code",
                 "pr_title":"Add file","pr_body":"","commit_message":"add file"}
                """;

        String result = tool.execute(input);

        assertThat(result).contains("GitHub API error");
    }

    @Test
    void execute_invalidJson_returnsHelpfulError() {
        String result = tool.execute("not json at all");

        assertThat(result).contains("ERROR");
        assertThat(result).contains("Invalid JSON");
    }

    @Test
    void describeApprovalRequest_happyPath_containsDiff() {
        String originalContent = "line1\nline2\nline3";
        String encodedContent = Base64.getMimeEncoder().encodeToString(originalContent.getBytes());

        when(client.get(contains("/contents/src/Main.java?ref=main")))
                .thenReturn("{\"content\":\"" + encodedContent + "\"}");

        String input = """
                {"owner":"owner","repo":"repo","base_branch":"main","branch_name":"fix-branch",
                 "file_path":"src/Main.java","new_file_content":"line1\\nline2-fixed\\nline3",
                 "pr_title":"Fix bug","pr_body":"","commit_message":"fix"}
                """;

        String description = tool.describeApprovalRequest(input);

        assertThat(description).contains("Fix bug");
        assertThat(description).contains("src/Main.java");
        assertThat(description).contains("-");
        assertThat(description).contains("+");
    }

    @Test
    void describeApprovalRequest_fileNotFound_showsNewFileMessage() {
        when(client.get(contains("/contents/")))
                .thenThrow(new GitHubApiException(404, "Not Found", "GET request failed"));

        String input = """
                {"owner":"owner","repo":"repo","base_branch":"main","branch_name":"fix-branch",
                 "file_path":"src/NewFile.java","new_file_content":"new content",
                 "pr_title":"Add new file","pr_body":"","commit_message":"add"}
                """;

        String description = tool.describeApprovalRequest(input);

        assertThat(description).contains("New file");
    }

    @Test
    void describeApprovalRequest_apiFailure_returnsFallback() {
        when(client.get(anyString()))
                .thenThrow(new GitHubApiException(500, "Server Error", "GET request failed"));

        String input = """
                {"owner":"owner","repo":"repo","base_branch":"main","branch_name":"fix-branch",
                 "file_path":"src/Main.java","new_file_content":"content",
                 "pr_title":"Fix","pr_body":"","commit_message":"fix"}
                """;

        String description = tool.describeApprovalRequest(input);

        assertThat(description).contains("could not generate diff preview");
    }

    @Test
    void generateDiff_showsCorrectMarkers() {
        String original = "line1\nline2\nline3";
        String updated = "line1\nline2-modified\nline3\nline4";

        String diff = GitHubCreatePRTool.generateDiff(original, updated);

        assertThat(diff).contains("  line1");
        assertThat(diff).contains("- line2");
        assertThat(diff).contains("+ line2-modified");
        assertThat(diff).contains("  line3");
        assertThat(diff).contains("+ line4");
    }

    @Test
    void approvalMetadata_returnsStructuredMap() {
        String input = """
                {"owner":"acme","repo":"widgets","base_branch":"main","branch_name":"fix/bug-1",
                 "file_path":"src/Widget.java","new_file_content":"code",
                 "pr_title":"Fix widget bug","pr_body":"","commit_message":"fix"}
                """;

        Map<String, String> metadata = tool.approvalMetadata(input);

        assertThat(metadata).containsEntry("pr_title", "Fix widget bug");
        assertThat(metadata).containsEntry("repo", "acme/widgets");
        assertThat(metadata).containsEntry("branch", "fix/bug-1");
        assertThat(metadata).containsEntry("file_path", "src/Widget.java");
    }
}
