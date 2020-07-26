package io.quarkus.backports;

import java.io.IOException;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GitHubServiceTest {

    @Inject
    GitHubService gitHub;

    @Test
    void test() throws IOException {
        System.out.println(gitHub.getBackportCandidatesPullRequests());
    }

}