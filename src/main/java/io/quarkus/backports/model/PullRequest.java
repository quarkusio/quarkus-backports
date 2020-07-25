package io.quarkus.backports.model;

import java.util.List;

public class PullRequest {
    public int number;

    public String url;

    public String title;

    public String createdAt;

    public Milestone milestone;

    public User author;

    public List<Commit> commits;

    public PullRequest(String number) {
        this.number = Integer.parseInt(number);
    }

    public PullRequest() {
    }
}
