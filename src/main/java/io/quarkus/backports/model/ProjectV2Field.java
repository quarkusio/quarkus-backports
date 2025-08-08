package io.quarkus.backports.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectV2Field {
    public String id;
    public String name;
    public String dataType;
    public List<ProjectV2FieldOption> options;
}
