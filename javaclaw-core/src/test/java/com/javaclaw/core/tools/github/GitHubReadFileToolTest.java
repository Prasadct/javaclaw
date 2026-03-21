package com.javaclaw.core.tools.github;

import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GitHubReadFileToolTest {

    private GitHubApiClient client;
    private GitHubReadFileTool tool;

    @BeforeEach
    void setUp() {
        client = mock(GitHubApiClient.class);
        tool = new GitHubReadFileTool(client);
    }

    @Test
    void definitionHasCorrectMetadata() {
        ToolDefinition def = tool.definition();
        assertThat(def.name()).isEqualTo("github_read_file");
        assertThat(def.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(def.description()).contains("file");
    }

    @Test
    void readsFileSuccessfully() {
        String fileContent = "public class Hello {}";
        String encoded = Base64.getMimeEncoder().encodeToString(fileContent.getBytes());
        String apiResponse = """
                {"content": "%s"}
                """.formatted(encoded);

        when(client.get("https://api.github.com/repos/acme/app/contents/src/Hello.java?ref=main"))
                .thenReturn(apiResponse);

        String result = tool.execute("""
                {"owner": "acme", "repo": "app", "path": "src/Hello.java"}
                """);

        assertThat(result).contains("File: src/Hello.java (branch: main)");
        assertThat(result).contains("public class Hello {}");
    }

    @Test
    void usesSpecifiedBranch() {
        String encoded = Base64.getMimeEncoder().encodeToString("content".getBytes());
        String apiResponse = """
                {"content": "%s"}
                """.formatted(encoded);

        when(client.get("https://api.github.com/repos/acme/app/contents/README.md?ref=develop"))
                .thenReturn(apiResponse);

        String result = tool.execute("""
                {"owner": "acme", "repo": "app", "path": "README.md", "branch": "develop"}
                """);

        assertThat(result).contains("branch: develop");
        verify(client).get(contains("ref=develop"));
    }

    @Test
    void defaultsToMainBranch() {
        String encoded = Base64.getMimeEncoder().encodeToString("content".getBytes());
        String apiResponse = """
                {"content": "%s"}
                """.formatted(encoded);

        when(client.get(contains("ref=main"))).thenReturn(apiResponse);

        String result = tool.execute("""
                {"owner": "acme", "repo": "app", "path": "file.txt"}
                """);

        assertThat(result).contains("branch: main");
        verify(client).get(contains("ref=main"));
    }

    @Test
    void rejectsFileTooLarge() {
        String largeContent = "x".repeat(60_000);
        String encoded = Base64.getEncoder().encodeToString(largeContent.getBytes());
        String apiResponse = """
                {"content": "%s"}
                """.formatted(encoded);

        when(client.get(anyString())).thenReturn(apiResponse);

        String result = tool.execute("""
                {"owner": "acme", "repo": "app", "path": "big.txt"}
                """);

        assertThat(result).contains("File too large");
    }

    @Test
    void handlesNotFound() {
        when(client.get(anyString()))
                .thenThrow(new GitHubApiException(404, "Not Found", "Not Found"));

        String result = tool.execute("""
                {"owner": "acme", "repo": "app", "path": "missing.txt"}
                """);

        assertThat(result).contains("File not found");
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
