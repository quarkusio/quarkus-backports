package io.quarkus.backports.model;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.enterprise.inject.spi.CDI;

import io.quarkus.backports.GitHubService;

public class PullRequest implements Comparable<PullRequest> {

    private static final Pattern BACKPORT_PULL_REQUEST_PATTERN = Pattern
            .compile("^[0-9]+\\.[0-9]+(\\.[0-9]+)*-backport.*");

    public String id;

    public int number;

    public String body;

    public String url;

    public String title;

    public Date createdAt;

    public boolean merged;

    public Date mergedAt;

    public Milestone milestone;

    public User author;

    public List<Commit> commits;

    public Set<Issue> linkedIssues;

    public Set<String> labels;

    public String headRefName;

    public String baseRefName;

    public boolean isBackport() {
        if (headRefName == null) {
            return false;
        }
        return BACKPORT_PULL_REQUEST_PATTERN.matcher(headRefName).matches();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PullRequest))
            return false;
        PullRequest that = (PullRequest) o;
        return number == that.number;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(number);
    }

    @Override
    public String toString() {
        return "PullRequest{" +
                "number=" + number +
                ", body='" + body + '\'' +
                ", url='" + url + '\'' +
                ", title='" + title + '\'' +
                ", createdAt=" + createdAt +
                ", mergedAt=" + mergedAt +
                ", milestone=" + milestone +
                ", author=" + author +
                ", commits=" + commits +
                ", linkedIssues=" + linkedIssues +
                ", labels=" + labels +
                '}';
    }

    @Override
    public int compareTo(PullRequest o) {
        if (merged) {
            return mergedAt.compareTo(o.mergedAt);
        } else {
            return Integer.compare(number, o.number);
        }
    }

    public static PullRequest fromString(String numberStr) throws IOException {
        int number = Integer.parseInt(numberStr);
        final GitHubService service = CDI.current().select(GitHubService.class).get();
        return service.getPullRequest(number);
    }

}