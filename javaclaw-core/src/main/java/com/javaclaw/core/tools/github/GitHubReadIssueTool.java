package com.javaclaw.core.tools.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.model.SimpleToolDefinition;
import com.javaclaw.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.StringJoiner;

public class GitHubReadIssueTool {

    private static final Logger log = LoggerFactory.getLogger(GitHubReadIssueTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final GitHubApiClient client;

    public GitHubReadIssueTool(String token) {
        this(new GitHubApiClient(token));
    }

    GitHubReadIssueTool(GitHubApiClient client) {
        this.client = client;
    }

    public ToolDefinition definition() {
        return new SimpleToolDefinition(
                "github_read_issue",
                "Reads a GitHub issue by number. Input: JSON with 'owner', 'repo', and 'issue_number' fields.",
                RiskLevel.LOW,
                this::execute
        );
    }

    String execute(String input) {
        JsonNode json;
        try {
            json = objectMapper.readTree(input);
        } catch (Exception e) {
            return "ERROR: Invalid JSON input. Expected: {\"owner\": \"...\", \"repo\": \"...\", \"issue_number\": 123}";
        }

        String owner = json.path("owner").asText("");
        String repo = json.path("repo").asText("");
        int issueNumber = json.path("issue_number").asInt(0);

        if (owner.isBlank() || repo.isBlank() || issueNumber <= 0) {
            return "ERROR: 'owner', 'repo', and 'issue_number' fields are required. 'issue_number' must be a positive integer.";
        }

        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/issues/" + issueNumber;

        try {
            String response = client.get(url);
            JsonNode issue = objectMapper.readTree(response);

            String title = issue.path("title").asText("");
            String state = issue.path("state").asText("");
            String body = issue.path("body").asText("");

            StringJoiner labels = new StringJoiner(", ");
            for (JsonNode label : issue.path("labels")) {
                labels.add(label.path("name").asText());
            }

            log.info("Read issue #{} from {}/{}", issueNumber, owner, repo);

            return "Issue #" + issueNumber + ": " + title + "\n" +
                    "State: " + state + "\n" +
                    "Labels: " + labels + "\n" +
                    "Body:\n" + body;
        } catch (GitHubApiException e) {
            if (e.getStatusCode() == 404) {
                return "Issue not found: " + owner + "/" + repo + "#" + issueNumber;
            }
            log.error("GitHub API error reading issue: {}", e.getMessage());
            return "GitHub API error: " + e.getMessage();
        } catch (Exception e) {
            log.error("Failed to parse GitHub response: {}", e.getMessage());
            return "ERROR: Failed to parse GitHub API response: " + e.getMessage();
        }
    }
}
