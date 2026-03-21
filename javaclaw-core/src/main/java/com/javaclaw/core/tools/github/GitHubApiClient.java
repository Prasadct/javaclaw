package com.javaclaw.core.tools.github;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

class GitHubApiClient {

    private final HttpClient httpClient;
    private final String token;

    GitHubApiClient(String token) {
        this.token = token;
        this.httpClient = HttpClient.newHttpClient();
    }

    String post(String url, String jsonBody) {
        return sendWithBody(url, "POST", jsonBody);
    }

    String put(String url, String jsonBody) {
        return sendWithBody(url, "PUT", jsonBody);
    }

    String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GitHubApiException(
                        response.statusCode(),
                        response.body(),
                        "Request failed: " + url
                );
            }

            return response.body();
        } catch (GitHubApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubApiException(0, "", "Failed to connect to GitHub API: " + e.getMessage());
        }
    }

    private String sendWithBody(String url, String method, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GitHubApiException(
                        response.statusCode(),
                        response.body(),
                        method + " request failed: " + url
                );
            }

            return response.body();
        } catch (GitHubApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubApiException(0, "", "Failed to connect to GitHub API: " + e.getMessage());
        }
    }
}
