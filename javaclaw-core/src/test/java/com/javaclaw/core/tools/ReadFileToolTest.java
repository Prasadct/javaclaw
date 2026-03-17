package com.javaclaw.core.tools;

import com.javaclaw.core.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadFileToolTest {

    @TempDir
    Path tempDir;

    private ReadFileTool tool;

    @BeforeEach
    void setUp() {
        tool = new ReadFileTool(tempDir);
    }

    @Test
    void definitionHasCorrectMetadata() {
        var def = tool.definition();
        assertThat(def.name()).isEqualTo("read_file");
        assertThat(def.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void readsFileSuccessfully() throws IOException {
        Files.writeString(tempDir.resolve("hello.txt"), "Hello, world!");

        String result = tool.execute("hello.txt");

        assertThat(result).isEqualTo("Hello, world!");
    }

    @Test
    void readsNestedFile() throws IOException {
        Path sub = tempDir.resolve("sub");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("nested.txt"), "nested content");

        String result = tool.execute("sub/nested.txt");

        assertThat(result).isEqualTo("nested content");
    }

    @Test
    void blocksPathTraversal() {
        String result = tool.execute("../../etc/passwd");

        assertThat(result).contains("ERROR").contains("Access denied");
    }

    @Test
    void returnsErrorForMissingFile() {
        String result = tool.execute("nonexistent.txt");

        assertThat(result).contains("ERROR").contains("File not found");
    }

    @Test
    void returnsErrorForDirectory() throws IOException {
        Files.createDirectories(tempDir.resolve("adir"));

        String result = tool.execute("adir");

        assertThat(result).contains("ERROR").contains("Not a regular file");
    }
}
