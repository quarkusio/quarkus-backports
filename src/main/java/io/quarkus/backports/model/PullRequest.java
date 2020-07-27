package io.quarkus.backports.model;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PullRequest {
    public int number;

    public String body;

    public String url;

    public String title;

    public Date createdAt;

    public Date mergedAt;

    public Milestone milestone;

    public User author;

    public List<Commit> commits;

    public Set<Issue> linkedIssues;

    public Set<String> labels;

    public PullRequest(String number) {
        this.number = Integer.parseInt(number);
    }

    public PullRequest() {
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PullRequest)) return false;
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
}