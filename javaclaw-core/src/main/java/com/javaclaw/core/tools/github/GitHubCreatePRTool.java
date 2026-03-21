package com.javaclaw.core.tools.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class GitHubCreatePRTool implements ToolDefinition {

    private static final Logger log = LoggerFactory.getLogger(GitHubCreatePRTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_DIFF_LINES = 60;

    private final GitHubApiClient client;

    public GitHubCreatePRTool(String token) {
        this(new GitHubApiClient(token));
    }

    GitHubCreatePRTool(GitHubApiClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "github_create_pr";
    }

    @Override
    public String description() {
        return "Creates a GitHub pull request with a new or modified file. " +
                "Input: JSON with 'owner', 'repo', 'base_branch', 'branch_name', 'file_path', " +
                "'new_file_content', 'pr_title', 'pr_body', 'commit_message' fields.";
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.HIGH;
    }

    @Override
    public Function<String, String> executor() {
        return this::execute;
    }

    @Override
    public String describeApprovalRequest(String input) {
        try {
            JsonNode json = objectMapper.readTree(input);
            String owner = json.path("owner").asText("");
            String repo = json.path("repo").asText("");
            String baseBranch = json.path("base_branch").asText("main");
            String filePath = json.path("file_path").asText("");
            String newContent = json.path("new_file_content").asText("");
            String prTitle = json.path("pr_title").asText("");

            var sb = new StringBuilder();
            sb.append("*Create Pull Request:* ").append(prTitle).append("\n");
            sb.append("Repository: ").append(owner).append("/").append(repo).append("\n");
            sb.append("File: `").append(filePath).append("`\n\n");

            // Try to fetch the current file to generate a diff
            String originalContent = fetchCurrentFile(owner, repo, filePath, baseBranch);
            if (originalContent == null) {
                sb.append("_New file — no existing version to diff against._\n");
            } else {
                String diff = generateDiff(originalContent, newContent);
                String[] diffLines = diff.split("\n");
                if (diffLines.length > MAX_DIFF_LINES) {
                    sb.append("```\n");
                    for (int i = 0; i < MAX_DIFF_LINES; i++) {
                        sb.append(diffLines[i]).append("\n");
                    }
                    sb.append("... (").append(diffLines.length - MAX_DIFF_LINES).append(" more lines)\n");
                    sb.append("```");
                } else {
                    sb.append("```\n").append(diff).append("\n```");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to generate approval description: {}", e.getMessage());
            return "Create Pull Request (could not generate diff preview: " + e.getMessage() + ")";
        }
    }

    @Override
    public Map<String, String> approvalMetadata(String input) {
        try {
            JsonNode json = objectMapper.readTree(input);
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("pr_title", json.path("pr_title").asText(""));
            metadata.put("repo", json.path("owner").asText("") + "/" + json.path("repo").asText(""));
            metadata.put("branch", json.path("branch_name").asText(""));
            metadata.put("file_path", json.path("file_path").asText(""));
            return metadata;
        } catch (Exception e) {
            return Map.of();
        }
    }

    String execute(String input) {
        JsonNode json;
        try {
            json = objectMapper.readTree(input);
        } catch (Exception e) {
            return "ERROR: Invalid JSON input. Expected fields: owner, repo, base_branch, branch_name, " +
                    "file_path, new_file_content, pr_title, pr_body, commit_message";
        }

        String owner = json.path("owner").asText("");
        String repo = json.path("repo").asText("");
        String baseBranch = json.path("base_branch").asText("main");
        String branchName = json.path("branch_name").asText("");
        String filePath = json.path("file_path").asText("");
        String newFileContent = json.path("new_file_content").asText("");
        String prTitle = json.path("pr_title").asText("");
        String prBody = json.path("pr_body").asText("");
        String commitMessage = json.path("commit_message").asText("");

        if (owner.isBlank() || repo.isBlank() || branchName.isBlank() || filePath.isBlank()
                || newFileContent.isBlank() || prTitle.isBlank() || commitMessage.isBlank()) {
            return "ERROR: Required fields: owner, repo, branch_name, file_path, new_file_content, pr_title, commit_message";
        }

        String apiBase = "https://api.github.com/repos/" + owner + "/" + repo;

        try {
            // Step 1: Get the SHA of the base branch
            String refResponse = client.get(apiBase + "/git/ref/heads/" + baseBranch);
            JsonNode refNode = objectMapper.readTree(refResponse);
            String baseSha = refNode.path("object").path("sha").asText();

            // Step 2: Create a new branch from base
            String createRefBody = objectMapper.writeValueAsString(Map.of(
                    "ref", "refs/heads/" + branchName,
                    "sha", baseSha
            ));
            try {
                client.post(apiBase + "/git/refs", createRefBody);
            } catch (GitHubApiException e) {
                if (e.getStatusCode() == 422) {
                    log.info("Branch '{}' already exists, continuing", branchName);
                } else {
                    throw e;
                }
            }

            // Step 3: Get current file SHA (if file exists)
            String fileSha = null;
            try {
                String fileResponse = client.get(apiBase + "/contents/" + filePath + "?ref=" + branchName);
                JsonNode fileNode = objectMapper.readTree(fileResponse);
                fileSha = fileNode.path("sha").asText();
            } catch (GitHubApiException e) {
                if (e.getStatusCode() != 404) {
                    throw e;
                }
                // File doesn't exist yet — creating new file
            }

            // Step 4: Create or update the file (commit)
            String encodedContent = Base64.getEncoder().encodeToString(newFileContent.getBytes());
            Map<String, Object> commitBody = new LinkedHashMap<>();
            commitBody.put("message", commitMessage);
            commitBody.put("content", encodedContent);
            commitBody.put("branch", branchName);
            if (fileSha != null) {
                commitBody.put("sha", fileSha);
            }
            client.put(apiBase + "/contents/" + filePath, objectMapper.writeValueAsString(commitBody));

            // Step 5: Create the pull request
            String prBody2 = objectMapper.writeValueAsString(Map.of(
                    "title", prTitle,
                    "body", prBody,
                    "head", branchName,
                    "base", baseBranch
            ));
            String prResponse = client.post(apiBase + "/pulls", prBody2);
            JsonNode prNode = objectMapper.readTree(prResponse);
            String prUrl = prNode.path("html_url").asText("");

            log.info("Created PR: {}", prUrl);
            return "Pull request created: " + prUrl;

        } catch (GitHubApiException e) {
            log.error("GitHub API error creating PR: {}", e.getMessage());
            return "GitHub API error: " + e.getMessage();
        } catch (Exception e) {
            log.error("Failed to create PR: {}", e.getMessage());
            return "ERROR: Failed to create pull request: " + e.getMessage();
        }
    }

    private String fetchCurrentFile(String owner, String repo, String filePath, String branch) {
        try {
            String url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + filePath + "?ref=" + branch;
            String response = client.get(url);
            JsonNode fileNode = objectMapper.readTree(response);
            String encodedContent = fileNode.path("content").asText("");
            byte[] decoded = Base64.getMimeDecoder().decode(encodedContent);
            return new String(decoded);
        } catch (GitHubApiException e) {
            if (e.getStatusCode() == 404) {
                return null; // File doesn't exist yet
            }
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch current file for diff: {}", e.getMessage());
            return null;
        }
    }

    static String generateDiff(String original, String updated) {
        String[] oldLines = original.split("\n", -1);
        String[] newLines = updated.split("\n", -1);

        // Simple LCS-based diff
        int[][] lcs = new int[oldLines.length + 1][newLines.length + 1];
        for (int i = oldLines.length - 1; i >= 0; i--) {
            for (int j = newLines.length - 1; j >= 0; j--) {
                if (oldLines[i].equals(newLines[j])) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        List<String> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < oldLines.length || j < newLines.length) {
            if (i < oldLines.length && j < newLines.length && oldLines[i].equals(newLines[j])) {
                result.add("  " + oldLines[i]);
                i++;
                j++;
            } else if (j < newLines.length && (i >= oldLines.length || lcs[i][j + 1] >= lcs[i + 1][j])) {
                result.add("+ " + newLines[j]);
                j++;
            } else if (i < oldLines.length) {
                result.add("- " + oldLines[i]);
                i++;
            }
        }

        return String.join("\n", result);
    }
}
