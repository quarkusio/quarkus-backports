package io.quarkus.backports;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedSearchIterable;

@ApplicationScoped
public class GitHubService {

    private final GitHub gitHub;

    private final GHRepository repository;

    private final String backportLabel;

    @Inject
    public GitHubService(
            @ConfigProperty(name = "backports.repository")
                    String repositoryName,
            @ConfigProperty(name = "backports.label")
                    String backportLabel) throws IOException {
        this.gitHub = GitHubBuilder.fromPropertyFile().build();
        this.repository = gitHub.getRepository(repositoryName);
        this.backportLabel = backportLabel;
    }

    public GHPullRequest getPullRequest(int pullRequestId) throws IOException {
        return repository.getPullRequest(pullRequestId);
    }

    public List<GHMilestone> getOpenMilestones() throws IOException {
        return repository.listMilestones(GHIssueState.OPEN).toList();
    }

    public GHMilestone getMilestone(int milestoneId) throws IOException {
        return repository.getMilestone(milestoneId);
    }

    public List<GHPullRequest> getBackportCandidatesPullRequests() throws IOException {
        PagedSearchIterable<GHIssue> issues = gitHub.searchIssues().isClosed()
                .q("is:pr")
                .q("label:" + backportLabel)
                .q("repo:" + repository.getFullName())
                .list()
                .withPageSize(1000);

        // Unfortunately, I haven't found a proper way to get GHPullRequest objects with a query
        // and we have too many closed ones to get them all and filter them locally
        List<GHPullRequest> pullRequests = new ArrayList<>();
        for (GHIssue issue : issues) {
            pullRequests.add(repository.getPullRequest(issue.getNumber()));
        }
        pullRequests.sort(PullRequestComparator.INSTANCE);
        return pullRequests;
    }

    public void markPullRequestAsBackported(GHPullRequest pullRequest, GHMilestone milestone) throws IOException {
        // this doesn't seem to work :/
        pullRequest.setMilestone(milestone);
        pullRequest.removeLabels(backportLabel);

        // we also need to affect the milestone to all the potentially linked issues
        // I haven't looked if there is already an API to get them (without parsing the content of the message)
    }

    public GHMilestone createMilestone(String title, String description) throws IOException {
        return repository.createMilestone(title, description);
    }

    private enum PullRequestComparator implements Comparator<GHPullRequest> {
        INSTANCE;

        @Override
        public int compare(GHPullRequest o1, GHPullRequest o2) {
            return o1.getMergedAt().compareTo(o2.getMergedAt());
        }
    }


}
