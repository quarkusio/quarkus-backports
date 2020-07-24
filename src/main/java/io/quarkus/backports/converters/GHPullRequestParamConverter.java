package io.quarkus.backports.converters;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.ext.ParamConverter;

import io.quarkus.backports.GitHubService;
import org.kohsuke.github.GHPullRequest;

@ApplicationScoped
public class GHPullRequestParamConverter implements ParamConverter<GHPullRequest> {

    @Inject
    GitHubService gitHubService;

    @Override
    public GHPullRequest fromString(String value) {
        try {
            return gitHubService.getPullRequest(Integer.parseInt(value));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString(GHPullRequest value) {
        return value.toString();
    }
}
