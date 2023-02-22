package io.quarkus.backports;

import java.io.IOException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Disabled("CI needs to be properly configured")
class GitHubServiceTest {

    @Inject
    GitHubService gitHub;

    @Test
    void test() throws IOException {
        System.out.println(gitHub.getBackportCandidatesPullRequests());
    }

}