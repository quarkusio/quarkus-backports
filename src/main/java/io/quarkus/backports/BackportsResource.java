package io.quarkus.backports;

import java.io.IOException;
import java.util.Collection;

import io.quarkus.logging.Log;
import io.quarkus.backports.model.ProjectV2;
import io.quarkus.backports.model.ProjectV2Field;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
import org.jboss.resteasy.reactive.RestQuery;

@Path("/")
public class BackportsResource {

    @Inject
    GitHubService gitHub;

    @ConfigProperty(name = "backports.repository")
    String repository;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance index(String repository, Collection<Milestone> milestones);

        public static native TemplateInstance backports(Milestone milestone, Collection<PullRequest> prs);

        public static native TemplateInstance createStatusOptionForMilestone(ProjectV2 projectV2, Milestone milestone, String statusFieldSettingsUrl, String refreshStatusFieldUrl);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @CacheInvalidateAll(cacheName = CacheNames.MILESTONES_CACHE_NAME)
    @Blocking
    public TemplateInstance index() throws IOException {
        return Templates.index(repository, gitHub.getOpenMilestones());
    }

    @GET
    @Path("/backports/{milestone}/")
    @Produces(MediaType.TEXT_HTML)
    @CacheInvalidateAll(cacheName = CacheNames.PULLREQUESTS_CACHE_NAME)
    @Blocking
    public TemplateInstance backports(@NotNull(message = "Invalid Milestone")  @RestPath final Milestone milestone) throws IOException {
        ProjectV2 projectV2 = gitHub.prepareRequirements(milestone);

        if (gitHub.isMilestonePresentInStatusField(projectV2.id, milestone)) {
            return Templates.backports(milestone, gitHub.getBackportCandidatesPullRequests());
        } else {
            return Templates.createStatusOptionForMilestone(projectV2, milestone, gitHub.getStatusFieldSettingsUrl(projectV2.number),
                    UriBuilder.fromPath("/backports/{milestone}/refresh-status-field/{projectId}").resolveTemplate("milestone", milestone.title()).resolveTemplate("projectId", projectV2.id).build().toString());
        }
    }

    @GET
    @Path("/backports/{milestone}/refresh-status-field/{projectId}")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response backportsRefreshStatusField(@NotNull(message = "Invalid Milestone")  @RestPath final Milestone milestone,
             @RestPath final String projectId) throws IOException {
        ProjectV2Field statusField = gitHub.refreshStatusField(projectId);
        if (!gitHub.isMilestonePresentInStatusField(projectId, milestone)) {
            throw new IllegalStateException("Make sure you create the appropriate column in the project board and refresh");
        }

        return Response.temporaryRedirect(UriBuilder.fromPath("/backports/{milestone}/").resolveTemplate("milestone", milestone.title()).build()).build();
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/backports/{milestone}/backported/{pullRequest}/")
    @Blocking
    public String markAsBackported(@NotNull(message = "Invalid Milestone") @RestPath Milestone milestone,
                                   @NotNull(message = "Invalid Pull Request") @RestPath PullRequest pullRequest) throws IOException {
        gitHub.markPullRequestAsBackported(pullRequest, milestone);
        Log.info("Backported PR to " + milestone.title() + ": " + pullRequest.url);
        pullRequest.commits.forEach(commit -> {
            Log.info("    Backported commit: URL=" +  commit.url + ", message=" + commit.message);
        });
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
