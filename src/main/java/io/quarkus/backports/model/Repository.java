package io.quarkus.backports.model;

public record Repository(String fullName, String owner, String name) {

    public static Repository fromString(String fullName) {
        String[] parts = fullName.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid repository name: " + fullName);
        }

        return new Repository(fullName, parts[0], parts[1]);
    }
}
