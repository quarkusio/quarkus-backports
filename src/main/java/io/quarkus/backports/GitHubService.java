package io.quarkus.backports;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.quarkus.backports.graphql.GraphQLClient;
import io.quarkus.backports.model.Commit;
import io.quarkus.backports.model.Issue;
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

    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    @Inject
    @RestClient
    GraphQLClient graphQLClient;

    private final String token;

    private final String repository;

    private final String backportLabel;

    /**
     * Necessary for the unset operation
     */
    private String backportLabelId;

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


    @PostConstruct
    void fetchBackportLabelID() {
        String[] ownerAndRepo = repository.split("/");
        final String query = Templates.findBackportLabelId(ownerAndRepo[0], ownerAndRepo[1], backportLabel).render();
        final JsonObject response = graphQLClient.graphql(token, new JsonObject().put("query", query));
        // Any errors?
        if (response.getJsonArray("errors") != null) {
            throw new RuntimeException(response.toString());
        }
        this.backportLabelId =
                response.getJsonObject("data").getJsonObject("repository").getJsonObject("label").getString("id");
    }

    @CacheResult(cacheName = "github-cache")
    public Collection<Milestone> getOpenMilestones() throws IOException {
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

    public Collection<PullRequest> getBackportCandidatesPullRequests() throws IOException {
        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.listPullRequests(repository, backportLabel).render()));
        // Any errors?
        if (response.getJsonArray("errors") != null) {
            throw new IOException(response.toString());
        }
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Set<PullRequest> prList = new TreeSet<>();
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
            pullRequest.id = pr.getString("id");
            pullRequest.number = pr.getInteger("number");
            try {
                pullRequest.createdAt = sdf.parse(pr.getString("createdAt"));
            } catch (ParseException ignore) {
            }
            try {
                pullRequest.mergedAt = sdf.parse(pr.getString("mergedAt"));
            } catch (ParseException ignore) {
            }
            pullRequest.title = pr.getString("title");
            pullRequest.url = pr.getString("url");
            pullRequest.body = pr.getString("body");
            pullRequest.author = pr.getJsonObject("author").mapTo(User.class);
            pullRequest.commits = commitList;

            // Labels
            pullRequest.labels = pr.getJsonObject("labels").getJsonArray("nodes").stream()
                    .map(JsonObject.class::cast)
                    .map(json -> json.getString("name"))
                    .collect(Collectors.toSet());
            // Linked issues are available through CONNECTED and DISCONNECTED events
            // As these events can happen multiple times, we need to retain only events in an odd number
            // (even means the issue was connected and disconnected)
            Set<Issue> issues = new TreeSet<>();
            final JsonArray timelineItems = pr.getJsonObject("timelineItems").getJsonArray("nodes");
            for (int j = 0; j < timelineItems.size(); j++) {
                Issue issue = timelineItems.getJsonObject(j).getJsonObject("subject").mapTo(Issue.class);
                // Add the issue to the Set. If it already exists, remove
                if (!issues.add(issue)) {
                    issues.remove(issue);
                }
            }
            pullRequest.linkedIssues = issues;
            prList.add(pullRequest);
        }
        return prList;
    }

    public void markPullRequestAsBackported(PullRequest pullRequest, Milestone milestone) throws IOException {
        // Set Milestone and remove the backport tag
        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.updatePullRequest().render())
                .put("variables", new JsonObject()
                        .put("inputMilestone", new JsonObject()
                                .put("pullRequestId", pullRequest.id)
                                .put("milestoneId", milestone.id))
                        .put("inputLabel", new JsonObject()
                                .put("labelableId", pullRequest.id)
                                .put("labelIds", backportLabelId)))
        );
        // Any errors?
        if (response.getJsonArray("errors") != null) {
            throw new IOException(response.toString());
        }

        // Update linked issues
        String issueGraphQL = Templates.updateIssue().render();
        for (Issue issue : pullRequest.linkedIssues) {
            // Set Milestone and remove the backport tag
            response = graphQLClient.graphql(token, new JsonObject()
                    .put("query", issueGraphQL)
                    .put("variables", new JsonObject()
                            .put("inputMilestone", new JsonObject()
                                    .put("id", issue.id)
                                    .put("milestoneId", milestone.id))
                            .put("inputLabel", new JsonObject()
                                    .put("labelableId", issue.id)
                                    .put("labelIds", backportLabelId)))
            );
            // Any errors?
            if (response.getJsonArray("errors") != null) {
                throw new IOException(response.toString());
            }
        }
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

        /**
         * Returns the backport label ID
         */
        public static native TemplateInstance findBackportLabelId(String owner, String repo, String label);

        /**
         * Update the Pull Request to the specified milestone and remove backport label
         */
        public static native TemplateInstance updatePullRequest();

        /**
         * Update the Pull Request to the specified milestone and remove backport label
         */
        public static native TemplateInstance updateIssue();


    }

}