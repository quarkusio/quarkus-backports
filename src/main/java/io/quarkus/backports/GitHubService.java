package io.quarkus.backports;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.quarkus.backports.graphql.GraphQLClient;
import io.quarkus.backports.model.Commit;
import io.quarkus.backports.model.Milestone;
import io.quarkus.backports.model.PullRequest;
import io.quarkus.backports.model.User;
import io.quarkus.cache.CacheResult;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.api.CheckedTemplate;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class GitHubService {

    @Inject
    @RestClient
    GraphQLClient graphQLClient;

    private final String token;

    private final String repository;

    private final String backportLabel;

    @Inject
    public GitHubService(
            @ConfigProperty(name = "backports.token")
                    String token,
            @ConfigProperty(name = "backports.repository")
                    String repositoryName,
            @ConfigProperty(name = "backports.label")
                    String backportLabel) {
        this.token = "Bearer " + token;
        this.repository = repositoryName;
        this.backportLabel = backportLabel;
    }

    @CacheResult(cacheName = "github-cache")
    public List<Milestone> getOpenMilestones() throws IOException {
        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.listMilestones(repository).render()));
        // Any errors?
        if (response.getJsonArray("errors") != null) {
            throw new IOException(response.toString());
        }
        List<Milestone> milestoneList = new ArrayList<>();
        JsonArray milestones = response.getJsonObject("data")
                .getJsonObject("search")
                .getJsonArray("nodes").getJsonObject(0)
                .getJsonObject("milestones").getJsonArray("nodes");
        for (int i = 0; i < milestones.size(); i++) {
            milestoneList.add(milestones.getJsonObject(i).mapTo(Milestone.class));
        }
        return milestoneList;
    }

    public List<PullRequest> getBackportCandidatesPullRequests() throws IOException {
        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.listPullRequests(repository, backportLabel).render()));
        // Any errors?
        if (response.getJsonArray("errors") != null) {
            throw new IOException(response.toString());
        }
        List<PullRequest> prList = new ArrayList<>();
        JsonArray pullRequests = response.getJsonObject("data")
                .getJsonObject("search")
                .getJsonArray("nodes");
        for (int i = 0; i < pullRequests.size(); i++) {
            JsonObject pr = pullRequests.getJsonObject(i);
            JsonArray commits = pr.getJsonObject("commits").getJsonArray("nodes");
            List<Commit> commitList = new ArrayList<>();
            for (int j = 0; j < commits.size(); j++) {
                JsonObject commitNode = commits.getJsonObject(j);
                Commit commit = commitNode.getJsonObject("commit").mapTo(Commit.class);
                commit.url = commitNode.getString("url");
                commitList.add(commit);
            }
            // Sort by commit date
            Collections.sort(commitList);
            PullRequest pullRequest = new PullRequest();
            pullRequest.number = pr.getInteger("number");
            pullRequest.createdAt = pr.getString("createdAt");
            pullRequest.title = pr.getString("title");
            pullRequest.url = pr.getString("url");
            pullRequest.author = pr.getJsonObject("author").mapTo(User.class);
            pullRequest.commits = commitList;
            prList.add(pullRequest);
        }
        return prList;
    }

    public void markPullRequestAsBackported(PullRequest pullRequest, Milestone milestone) throws IOException {
    }

    public Milestone createMilestone(String title, String description) throws IOException {
        return null;
    }

    @CheckedTemplate
    private static class Templates {
        /**
         * Returns all the milestones from the repository
         */
        public static native TemplateInstance listMilestones(String repo);

        /**
         * Returns the (closed?) pull requests that match the specified label
         */
        public static native TemplateInstance listPullRequests(String repo, String label);
    }

}
