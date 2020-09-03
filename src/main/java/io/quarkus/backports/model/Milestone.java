package io.quarkus.backports.model;

import java.io.IOException;
import java.util.Objects;

import javax.enterprise.inject.spi.CDI;

import io.quarkus.backports.GitHubService;

public class Milestone {
    public String id;

    public String title;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Milestone)) return false;
        Milestone milestone = (Milestone) o;
        return Objects.equals(title, milestone.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title);
    }

    @Override
    public String toString() {
        return "Milestone{" +
                "id='" + id + "', " +
                "title='" + title + '\'' +
                '}';
    }

    public static Milestone fromString(String title) throws IOException {
        final GitHubService service = CDI.current().select(GitHubService.class).get();
        return service.getOpenMilestones().stream()
                .filter(milestone -> milestone.title.equals(title))
                .findFirst()
                .orElse(null);
    }
}
