package com.javaclaw.core.tools;

import com.javaclaw.core.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ShellCommandToolTest {

    @TempDir
    Path tempDir;

    private ShellCommandTool tool;

    @BeforeEach
    void setUp() {
        tool = new ShellCommandTool(tempDir);
    }

    @Test
    void definitionHasCorrectMetadata() {
        var def = tool.definition();
        assertThat(def.name()).isEqualTo("run_command");
        assertThat(def.riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void executesSimpleCommand() {
        String result = tool.execute("echo hello");

        assertThat(result).contains("Exit code: 0");
        assertThat(result).contains("hello");
    }

    @Test
    void capturesStdout() {
        String result = tool.execute("echo 'line1' && echo 'line2'");

        assertThat(result).contains("STDOUT:");
        assertThat(result).contains("line1");
        assertThat(result).contains("line2");
    }

    @Test
    void capturesStderr() {
        String result = tool.execute("echo 'err' >&2");

        assertThat(result).contains("STDERR:");
        assertThat(result).contains("err");
    }

    @Test
    void reportsNonZeroExitCode() {
        String result = tool.execute("exit 42");

        assertThat(result).contains("Exit code: 42");
    }

    @Test
    void handlesEmptyInput() {
        String result = tool.execute("");

        assertThat(result).contains("ERROR").contains("No command provided");
    }

    @Test
    void handlesNullInput() {
        String result = tool.execute(null);

        assertThat(result).contains("ERROR").contains("No command provided");
    }

    @Test
    void runsInWorkingDirectory() {
        String result = tool.execute("pwd");

        assertThat(result).contains(tempDir.toAbsolutePath().toString());
    }
}
