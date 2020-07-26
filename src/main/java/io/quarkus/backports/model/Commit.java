package io.quarkus.backports.model;

import java.util.Objects;

public class Commit {
    public String abbreviatedOid;
    public String message;
    public String url;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Commit)) return false;
        Commit commit = (Commit) o;
        return abbreviatedOid.equals(commit.abbreviatedOid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(abbreviatedOid);
    }

    @Override
    public String toString() {
        return "Commit{" +
                "abbreviatedOid='" + abbreviatedOid + '\'' +
                ", message='" + message + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
