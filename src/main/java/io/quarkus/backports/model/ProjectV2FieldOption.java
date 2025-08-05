package io.quarkus.backports.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectV2FieldOption {
    public String id;
    public String name;
    public String color;
    public String description;
}