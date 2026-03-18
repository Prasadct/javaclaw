# Contributing to Javaclaw

Thanks for your interest in contributing!

## Getting started

1. Fork the repo and clone your fork
2. Make sure you have Java 21 and Maven 3.9+ installed
3. Build and run tests: `mvn clean test`

## Making changes

1. Create a branch from `main`
2. Make your changes
3. Add or update tests as needed
4. Run `mvn clean test` and make sure everything passes
5. Open a pull request with a clear description of the change

## Code style

- Follow existing conventions in the codebase
- Use meaningful names for classes, methods, and variables
- Keep methods focused — one method, one responsibility
- Add SLF4J logging for significant operations

## Adding a new tool

1. Create a class in `com.javaclaw.core.tools` (see `ReadFileTool` for reference)
2. Return a `ToolDefinition` from a `definition()` method
3. Set the appropriate `RiskLevel` (LOW, MEDIUM, HIGH)
4. Write unit tests
5. Register it as a Spring bean in your application config

## Reporting issues

Open an issue with:
- What you expected to happen
- What actually happened
- Steps to reproduce
- Java version and OS

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
