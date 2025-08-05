package io.quarkus.backports.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectV2 {
    public String id;
    public String title;
    public Integer number;
}