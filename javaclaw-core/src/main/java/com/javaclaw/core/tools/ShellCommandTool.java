package com.javaclaw.core.tools;

import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class ShellCommandTool {

    private static final Logger log = LoggerFactory.getLogger(ShellCommandTool.class);
    private static final long TIMEOUT_SECONDS = 30;

    private final Path workingDirectory;

    public ShellCommandTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    public ToolDefinition definition() {
        return new ToolDefinition(
                "run_command",
                "Runs a shell command. Input: the command to execute.",
                RiskLevel.HIGH,
                this::execute
        );
    }

    String execute(String input) {
        if (input == null || input.isBlank()) {
            return "ERROR: No command provided.";
        }

        log.info("Executing shell command: {}", input);

        ProcessBuilder pb = new ProcessBuilder("sh", "-c", input)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(false);

        try {
            Process process = pb.start();

            String stdout;
            String stderr;
            try (var stdoutStream = process.getInputStream();
                 var stderrStream = process.getErrorStream()) {
                stdout = new String(stdoutStream.readAllBytes());
                stderr = new String(stderrStream.readAllBytes());
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Command timed out after {}s: {}", TIMEOUT_SECONDS, input);
                return "ERROR: Command timed out after " + TIMEOUT_SECONDS + " seconds.";
            }

            int exitCode = process.exitValue();
            log.info("Command exited with code {}", exitCode);

            var result = new StringBuilder();
            result.append("Exit code: ").append(exitCode).append("\n");
            if (!stdout.isBlank()) {
                result.append("STDOUT:\n").append(stdout);
            }
            if (!stderr.isBlank()) {
                result.append("STDERR:\n").append(stderr);
            }
            return result.toString();

        } catch (IOException e) {
            log.error("Failed to execute command: {}", e.getMessage());
            return "ERROR: Failed to execute command: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "ERROR: Command execution interrupted.";
        }
    }
}
