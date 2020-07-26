package io.quarkus.backports.model;

import java.util.Objects;

public class Milestone {
    public String title;

    public Milestone(String title) {
        this.title = title;
    }

    public Milestone() {
    }

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
                "title='" + title + '\'' +
                '}';
    }
}
