package com.javaclaw.core.tools.github;

public class GitHubApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public GitHubApiException(int statusCode, String responseBody, String message) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    @Override
    public String getMessage() {
        return "GitHub API error (HTTP " + statusCode + "): " + super.getMessage() + " — " + responseBody;
    }
}
