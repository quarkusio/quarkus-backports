package io.quarkus.backports.model;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.enterprise.inject.spi.CDI;

import io.quarkus.backports.GitHubService;

public class PullRequest implements Comparable<PullRequest> {

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
        return Objects.hash(number);
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