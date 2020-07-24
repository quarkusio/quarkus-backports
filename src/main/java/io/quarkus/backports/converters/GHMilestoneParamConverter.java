package io.quarkus.backports.converters;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.ext.ParamConverter;

import io.quarkus.backports.GitHubService;
import org.kohsuke.github.GHMilestone;

@ApplicationScoped
public class GHMilestoneParamConverter implements ParamConverter<GHMilestone> {

    @Inject
    GitHubService gitHubService;

    @Override
    public GHMilestone fromString(String value) {
        try {
            return gitHubService.getMilestone(Integer.parseInt(value));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString(GHMilestone value) {
        return value.toString();
    }
}
