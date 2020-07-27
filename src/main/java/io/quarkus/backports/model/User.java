package io.quarkus.backports.model;

import java.util.Objects;

public class User {
    public String login;
    public String avatarUrl;
    public String url;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return login.equals(user.login);
    }

    @Override
    public int hashCode() {
        return Objects.hash(login);
    }

    @Override
    public String toString() {
        return "User{" +
                "login='" + login + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
