package io.quarkus.backports.model;

import java.util.Objects;

public class PossibleFollowupPullRequest {

    public Integer id;

    public String title;

    public String url;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PossibleFollowupPullRequest that = (PossibleFollowupPullRequest) o;
        return Objects.equals(id, that.id) && Objects.equals(title, that.title) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, url);
    }

    @Override
    public String toString() {
        return "PossibleFollowupPullRequest{" +
               "id=" + id +
               ", title='" + title + '\'' +
               ", url='" + url + '\'' +
               '}';
    }
}
