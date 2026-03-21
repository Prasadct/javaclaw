package com.javaclaw.core.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.model.SimpleToolDefinition;
import com.javaclaw.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class SearchCodeTool {

    private static final Logger log = LoggerFactory.getLogger(SearchCodeTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_RESULTS = 20;

    private final Path baseDirectory;

    public SearchCodeTool(Path baseDirectory) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    public ToolDefinition definition() {
        return new SimpleToolDefinition(
                "search_code",
                "Searches for a pattern in LOCAL files within a directory on this server. Not for GitHub repositories — use github_read_file instead. Input: JSON with 'pattern' (regex) and 'directory' (relative path) fields.",
                RiskLevel.LOW,
                this::execute
        );
    }

    String execute(String input) {
        JsonNode json;
        try {
            json = objectMapper.readTree(input);
        } catch (Exception e) {
            return "ERROR: Invalid JSON input. Expected: {\"pattern\": \"...\", \"directory\": \"...\"}";
        }

        String patternStr = json.path("pattern").asText("");
        String directory = json.path("directory").asText(".");

        if (patternStr.isBlank()) {
            return "ERROR: 'pattern' field is required.";
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr);
        } catch (PatternSyntaxException e) {
            return "ERROR: Invalid regex pattern: " + e.getMessage();
        }

        Path searchDir = baseDirectory.resolve(directory).toAbsolutePath().normalize();
        if (!searchDir.startsWith(baseDirectory)) {
            log.warn("Path traversal attempt blocked: {}", directory);
            return "ERROR: Access denied — directory is outside the allowed base directory.";
        }

        if (!Files.isDirectory(searchDir)) {
            return "ERROR: Directory not found: " + directory;
        }

        var results = new StringBuilder();
        int matchCount = 0;

        try (Stream<Path> paths = Files.walk(searchDir)) {
            var files = paths.filter(Files::isRegularFile).toList();

            for (Path file : files) {
                if (matchCount >= MAX_RESULTS) break;

                try {
                    var lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        if (matchCount >= MAX_RESULTS) break;

                        if (pattern.matcher(lines.get(i)).find()) {
                            Path relative = baseDirectory.relativize(file);
                            int lineNum = i + 1;
                            results.append(relative).append(":").append(lineNum)
                                    .append(": ").append(lines.get(i).strip()).append("\n");
                            matchCount++;
                        }
                    }
                } catch (IOException e) {
                    // Skip files that can't be read (binary files, etc.)
                }
            }
        } catch (IOException e) {
            log.error("Failed to walk directory {}: {}", searchDir, e.getMessage());
            return "ERROR: Failed to search directory: " + e.getMessage();
        }

        if (matchCount == 0) {
            return "No matches found for pattern: " + patternStr;
        }

        log.info("Search for '{}' found {} matches", patternStr, matchCount);
        String header = "Found " + matchCount + " match(es)" +
                (matchCount >= MAX_RESULTS ? " (limit reached)" : "") + ":\n";
        return header + results;
    }
}
