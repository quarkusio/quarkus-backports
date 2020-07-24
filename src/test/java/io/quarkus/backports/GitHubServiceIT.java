package io.quarkus.backports;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GitHubServiceIT {

    static GitHubService GITHUB;

    @BeforeAll
    static void setUp() throws IOException {
        GITHUB = new GitHubService("gastaldi/backport-repo", "triage/backport?");
    }

    @Test
    void test() throws IOException {
        System.out.println(GITHUB.getBackportCandidatesPullRequests());
    }

}