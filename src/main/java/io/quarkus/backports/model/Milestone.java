package io.quarkus.backports.model;

import java.io.IOException;
import java.util.Objects;

import jakarta.enterprise.inject.spi.CDI;

import io.quarkus.backports.GitHubService;

public record Milestone(String id, String title, String minorVersion) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Milestone milestone)) return false;
        return Objects.equals(title, milestone.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title);
    }

    public static Milestone fromString(String title) throws IOException {
        final GitHubService service = CDI.current().select(GitHubService.class).get();
        return service.getOpenMilestones().stream()
                .filter(milestone -> milestone.title.equals(title))
                .findFirst()
                .orElse(null);
    }
}
