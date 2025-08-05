package io.quarkus.backports.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectV2Field {
    public String id;
    public String name;
    public String dataType;
    public List<ProjectV2FieldOption> options;
}