package io.quarkus.backports;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.quarkus.qute.TemplateExtension;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.api.CheckedTemplate;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;

@Path("/")
public class BackportsResource {

    @Inject
    GitHubService gitHub;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance index(List<GHMilestone> milestones);

        public static native TemplateInstance backports(GHMilestone milestone, List<GHPullRequest> prs);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() throws IOException {
        return Templates.index(gitHub.getOpenMilestones());
    }

    @GET
    @Path("/backports/{milestoneId}/")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance backports(@PathParam GHMilestone milestone) throws IOException {
        List<GHPullRequest> pullRequests = gitHub.getBackportCandidatesPullRequests();
        return Templates.backports(milestone, pullRequests);
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/backports/{milestoneId}/backported/{pullRequestId}/")
    public String markAsBackported(@PathParam GHMilestone milestone, @PathParam GHPullRequest pullRequest) throws IOException {
        gitHub.markPullRequestAsBackported(pullRequest, milestone);
        return "SUCCESS";
    }

    @TemplateExtension
    static class Extensions {

        static String abbreviatedMessage(GHPullRequestCommitDetail commit) {
            String message = commit.getCommit().getMessage();
            int newLine = message.indexOf('\n');
            if (newLine < 0) {
                return message;
            }

            return message.substring(0, newLine);
        }

        static String abbreviatedSha(GHPullRequestCommitDetail commit) {
            return commit.getSha().substring(0, 7);
        }
    }
}