package io.quarkus.backports;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.quarkus.backports.model.Commit;
import io.quarkus.backports.model.Milestone;
import io.quarkus.backports.model.PullRequest;
import io.quarkus.qute.TemplateExtension;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.api.CheckedTemplate;
import org.jboss.resteasy.annotations.jaxrs.PathParam;

@Path("/")
public class BackportsResource {

    @Inject
    GitHubService gitHub;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance index(List<Milestone> milestones);

        public static native TemplateInstance backports(Milestone milestone, List<PullRequest> prs);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() throws IOException {
        return Templates.index(gitHub.getOpenMilestones());
    }

    @GET
    @Path("/backports/{milestone}/")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance backports(@PathParam("milestone") final Milestone milestone) throws IOException {
        List<PullRequest> pullRequests = gitHub.getBackportCandidatesPullRequests();
        //TODO: Review it, because the title is not sent in the request
        Milestone titledMilestone = gitHub.getOpenMilestones()
                .stream()
                .filter(m -> m.number == milestone.number)
                .findFirst()
                .orElse(milestone);
        return Templates.backports(titledMilestone, pullRequests);
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/backports/{milestone}/backported/{pullRequest}/")
    public String markAsBackported(@PathParam("milestone") Milestone milestone,
                                   @PathParam("pullRequest") PullRequest pullRequest) throws IOException {
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

        static String abbreviatedSha(Commit commit) {
            return commit.abbreviatedOid.substring(0, 7);
        }
    }
}