package io.quarkus.backports;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedSearchIterable;

import io.quarkus.qute.TemplateExtension;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.api.CheckedTemplate;

@Path("/")
public class BackportsResource {

    @ConfigProperty(name = "backports.repository")
    String repositoryName;

    @ConfigProperty(name = "backports.label")
    String backportLabel;

    private GitHub github;

    private GHRepository repository;

    @PostConstruct
    public void initialize() {
        try {
            this.github = GitHubBuilder.fromPropertyFile().build();
            this.repository = github.getRepository(repositoryName);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to connect to GitHub API", e);
        }
    }

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance index(List<GHMilestone> milestones);

        public static native TemplateInstance backports(GHMilestone milestone, List<GHPullRequest> prs);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() throws IOException {
        return Templates.index(repository.listMilestones(GHIssueState.OPEN).toList());
    }

    @GET
    @Path("/backports/{milestoneId}/")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance backports(@PathParam Integer milestoneId) throws IOException {
        GHMilestone milestone = getMilestone(milestoneId);

        PagedSearchIterable<GHIssue> issues = github.searchIssues().isClosed()
                .q("is:pr")
                .q("label:" + backportLabel)
                .q("repo:" + repositoryName)
                .list()
                .withPageSize(1000);

        // Unfortunately, I haven't found a proper way to get GHPullRequest objects with a query
        // and we have too many closed ones to get them all and filter them locally
        List<GHPullRequest> pullRequests = new ArrayList<>();

        for (GHIssue issue : issues) {
            pullRequests.add(repository.getPullRequest(issue.getNumber()));
        }

        Collections.sort(pullRequests, PullRequestComparator.INSTANCE);

        return Templates.backports(milestone, pullRequests);
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/backports/{milestoneId}/backported/{pullRequestId}/")
    public String markAsBackported(@PathParam Integer milestoneId, @PathParam Integer pullRequestId) throws IOException {
        GHMilestone milestone = getMilestone(milestoneId);
        GHPullRequest pullRequest = repository.getPullRequest(pullRequestId);

        // this doesn't seem to work :/
        pullRequest.setMilestone(milestone);
        pullRequest.removeLabels(backportLabel);

        // we also need to affect the milestone to all the potentially linked issues
        // I haven't looked if there is already an API to get them (without parsing the content of the message)

        return "SUCCESS";
    }

    private GHMilestone getMilestone(Integer milestoneId) {
        try {
            return repository.getMilestone(milestoneId);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to find milestone " + milestoneId, e);
        }
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

    private static class PullRequestComparator implements Comparator<GHPullRequest> {

        private static PullRequestComparator INSTANCE = new PullRequestComparator();

        @Override
        public int compare(GHPullRequest o1, GHPullRequest o2) {
            return o1.getMergedAt().compareTo(o2.getMergedAt());
        }
    }
}