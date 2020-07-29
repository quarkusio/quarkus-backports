package io.quarkus.backports;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssueExtractorTest {

    @Test
    void extractIssueNumbers() {
        String content = "The description of my issue... Fix #5\n" +
                "\n" +
                "Fix: #6\n" +
                "Closed #7 #8\n" +
                "Fixes #9657\n" +
                "Text resolves #9\n" +
                "\n" +
                "Fixes https://github.com/gsmet/backports-test/issues/12 (issue with a link)\n" +
                "\n" +
                "And other things...";
        IssueExtractor extractor = new IssueExtractor("gsmet/backports-test");
        Set<Integer> issueNumbers = extractor.extractIssueNumbers(content);
        assertThat(issueNumbers).containsExactly(5, 6, 7, 9, 12, 9657);
    }
}