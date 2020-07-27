package io.quarkus.backports.model;

import java.util.Date;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

public class Commit implements Comparable<Commit> {
    public String abbreviatedOid;

    public String message;

    public String url;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Date committedDate;

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

    @Override
    public int compareTo(Commit o) {
        return committedDate.compareTo(o.committedDate);
    }
}
