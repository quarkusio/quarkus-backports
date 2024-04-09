package io.quarkus.backports;

import java.io.IOException;
import java.util.Collection;

import org.jboss.resteasy.reactive.RestPath;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.backports.model.Commit;
import io.quarkus.backports.model.Milestone;
import io.quarkus.backports.model.PullRequest;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateExtension;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.common.annotation.Blocking;

@Path("/")
public class BackportsResource {

    @Inject
    GitHubService gitHub;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance index(Collection<Milestone> milestones);

        public static native TemplateInstance backports(Milestone milestone, Collection<PullRequest> prs);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @CacheInvalidateAll(cacheName = CacheNames.MILESTONES_CACHE_NAME)
    @Blocking
    public TemplateInstance index() throws IOException {
        return Templates.index(gitHub.getOpenMilestones());
    }

    @GET
    @Path("/backports/{milestone}/")
    @Produces(MediaType.TEXT_HTML)
    @CacheInvalidateAll(cacheName = CacheNames.PULLREQUESTS_CACHE_NAME)
    @Blocking
    public TemplateInstance backports(@NotNull(message = "Invalid Milestone")  @RestPath final Milestone milestone) throws IOException {
        return Templates.backports(milestone, gitHub.getBackportCandidatesPullRequests());
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/backports/{milestone}/backported/{pullRequest}/")
    @Blocking
    public String markAsBackported(@NotNull(message = "Invalid Milestone") @RestPath Milestone milestone,
                                   @NotNull(message = "Invalid Pull Request") @RestPath PullRequest pullRequest) throws IOException {
        gitHub.markPullRequestAsBackported(pullRequest, milestone);
        return "SUCCESS";
    }

    @TemplateExtension
    static class Extensions {

        static String abbreviatedMessage(Commit commit) {
            String message = commit.message;
            int newLine = message.indexOf('\n');
            if (newLine < 0) {
                return message;
            }

            return message.substring(0, newLine);
        }
    }
}
