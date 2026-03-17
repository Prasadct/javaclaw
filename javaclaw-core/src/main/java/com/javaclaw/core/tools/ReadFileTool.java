package com.javaclaw.core.tools;

import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReadFileTool {

    private static final Logger log = LoggerFactory.getLogger(ReadFileTool.class);

    private final Path baseDirectory;

    public ReadFileTool(Path baseDirectory) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    public ToolDefinition definition() {
        return new ToolDefinition(
                "read_file",
                "Reads the content of a file at the given path. Input: file path as string.",
                RiskLevel.LOW,
                this::execute
        );
    }

    String execute(String input) {
        Path target = baseDirectory.resolve(input).toAbsolutePath().normalize();

        if (!target.startsWith(baseDirectory)) {
            log.warn("Path traversal attempt blocked: {}", input);
            return "ERROR: Access denied — path is outside the allowed base directory.";
        }

        if (!Files.exists(target)) {
            log.info("File not found: {}", target);
            return "ERROR: File not found: " + input;
        }

        if (!Files.isRegularFile(target)) {
            return "ERROR: Not a regular file: " + input;
        }

        try {
            String content = Files.readString(target);
            log.info("Read file: {} ({} chars)", target, content.length());
            return content;
        } catch (IOException e) {
            log.error("Failed to read file {}: {}", target, e.getMessage());
            return "ERROR: Failed to read file: " + e.getMessage();
        }
    }
}
