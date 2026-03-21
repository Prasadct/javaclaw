package com.javaclaw.core.tools.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.model.SimpleToolDefinition;
import com.javaclaw.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

public class GitHubReadFileTool {

    private static final Logger log = LoggerFactory.getLogger(GitHubReadFileTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_FILE_SIZE = 50_000;

    private final GitHubApiClient client;

    public GitHubReadFileTool(String token) {
        this(new GitHubApiClient(token));
    }

    GitHubReadFileTool(GitHubApiClient client) {
        this.client = client;
    }

    public ToolDefinition definition() {
        return new SimpleToolDefinition(
                "github_read_file",
                "Reads a file from a GitHub repository. Input: JSON with 'owner', 'repo', 'path', and optional 'branch' (default: 'main') fields.",
                RiskLevel.LOW,
                this::execute
        );
    }

    String execute(String input) {
        JsonNode json;
        try {
            json = objectMapper.readTree(input);
        } catch (Exception e) {
            return "ERROR: Invalid JSON input. Expected: {\"owner\": \"...\", \"repo\": \"...\", \"path\": \"...\", \"branch\": \"main\"}";
        }

        String owner = json.path("owner").asText("");
        String repo = json.path("repo").asText("");
        String path = json.path("path").asText("");
        String branch = json.path("branch").asText("main");

        if (owner.isBlank() || repo.isBlank() || path.isBlank()) {
            return "ERROR: 'owner', 'repo', and 'path' fields are required.";
        }

        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + branch;

        try {
            String response = client.get(url);
            JsonNode file = objectMapper.readTree(response);

            String encodedContent = file.path("content").asText("");
            byte[] decoded = Base64.getMimeDecoder().decode(encodedContent);
            String content = new String(decoded);

            if (content.length() > MAX_FILE_SIZE) {
                return "File too large: " + path + " (" + content.length() + " characters). Maximum allowed: " + MAX_FILE_SIZE + " characters.";
            }

            log.info("Read file {} from {}/{} (branch: {})", path, owner, repo, branch);

            return "File: " + path + " (branch: " + branch + ")\n" +
                    "----------------------------------------\n" +
                    content;
        } catch (GitHubApiException e) {
            if (e.getStatusCode() == 404) {
                return "File not found: " + owner + "/" + repo + "/" + path + " (branch: " + branch + ")";
            }
            log.error("GitHub API error reading file: {}", e.getMessage());
            return "GitHub API error: " + e.getMessage();
        } catch (Exception e) {
            log.error("Failed to parse GitHub response: {}", e.getMessage());
            return "ERROR: Failed to parse GitHub API response: " + e.getMessage();
        }
    }
}
