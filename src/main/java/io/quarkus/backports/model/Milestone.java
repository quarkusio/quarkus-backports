package io.quarkus.backports.model;

public class Milestone {
    public int number;

    public String title;

    public Milestone(String number) {
        this.number = Integer.parseInt(number);
    }

    public Milestone() {
    }

}
