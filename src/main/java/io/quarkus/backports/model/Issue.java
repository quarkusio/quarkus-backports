package io.quarkus.backports.model;

import java.util.Objects;

public class Issue implements Comparable<Issue> {
    public int number;

    public String title;
    public String body;
    public String url;
    public User author;

    public Issue(String number) {
        this.number = Integer.parseInt(number);
    }

    public Issue() {

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Issue)) return false;
        Issue issue = (Issue) o;
        return number == issue.number;
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }

    @Override
    public int compareTo(Issue o) {
        return number - o.number;
    }

    @Override
    public String toString() {
        return "Issue{" +
                "number=" + number +
                ", title='" + title + '\'' +
                ", body='" + body + '\'' +
                ", url='" + url + '\'' +
                ", author=" + author +
                '}';
    }
}
