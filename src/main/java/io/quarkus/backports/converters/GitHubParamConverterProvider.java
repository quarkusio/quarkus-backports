package io.quarkus.backports.converters;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHPullRequest;

@Provider
@ApplicationScoped
public class GitHubParamConverterProvider implements ParamConverterProvider {

    @Inject
    GHMilestoneParamConverter milestone;

    @Inject
    GHPullRequestParamConverter pr;

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        ParamConverter<T> result = null;
        if (rawType == GHMilestone.class) {
            result = (ParamConverter<T>) milestone;
        } else if (rawType == GHPullRequest.class) {
            result = (ParamConverter<T>) pr;
        }
        return result;
    }
}
