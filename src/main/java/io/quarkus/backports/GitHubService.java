package io.quarkus.backports;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import io.quarkus.backports.graphql.GraphQLClient;
import io.quarkus.backports.model.Commit;
import io.quarkus.backports.model.Issue;
import io.quarkus.backports.model.Milestone;
import io.quarkus.backports.model.ProjectV2;
import io.quarkus.backports.model.ProjectV2Field;
import io.quarkus.backports.model.ProjectV2FieldOption;
import io.quarkus.backports.model.ProjectV2Item;
import io.quarkus.backports.model.PullRequest;
import io.quarkus.backports.model.Repository;
import io.quarkus.backports.model.User;
import io.quarkus.cache.CacheResult;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class GitHubService {

    private static final Logger LOG = Logger.getLogger(GitHubService.class);

    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String PROJECT_NAME = "Backports for %s";
    private static final String OPTION_DESCRIPTION = "Backports for %s";
    private static final String STATUS_FIELD = "Status";
    private static final Pattern MICRO_VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+\\.\\d+(\\..*)*");
    private static final String COLUMN_COLOR = "BLUE";
    private static final String STATUS_FIELD_SETTINGS_URL = "https://github.com/orgs/%s/projects/%s/settings/fields/Status";
    private static final String PULL_REQUESTS_FOR_BACKPORT_LABEL_URL = "https://github.com/%s/issues?q=state%%3Aclosed%%20is%%3Amerged%%20label%%3A%s";
    private static final String OPEN_PULL_REQUESTS_TARGETING_BRANCH_LABEL_URL = "https://github.com/%s/issues?q=state%%3Aopen%%20base%%3A%s";
    private static final String MERGED_PULL_REQUESTS_TARGETING_BRANCH_WITH_NO_MILESTONE_LABEL_URL = "https://github.com/%s/issues?q=state%%3Aclosed%%20is%%3Amerged%%20no%%3Amilestone%%20base%%3A%s";

    @Inject
    @RestClient
    GraphQLClient graphQLClient;

    @Inject
    IssueExtractor issueExtractor;

    private final String token;

    private final Repository repository;

    private final String backportLabel;

    /**
     * Necessary for the unset operation
     */
    private String backportLabelId;

    /**
     * Cache for ownerId
     */
    private final Map<String, String> ownerIdCache = new ConcurrentHashMap<>();

    /**
     * Cache for repositoryId
     */
    private final Map<Repository, String> repositoryIdCache = new ConcurrentHashMap<>();

    /**
     * Cache for ProjectV2 objects by minor version
     */
    private final Map<String, ProjectV2> projectCache = new ConcurrentHashMap<>();

    /**
     * Cache for ProjectV2Field objects by minor version
     */
    private final Map<String, ProjectV2Field> statusFieldCache = new ConcurrentHashMap<>();

    /**
     * Cache for pull requests keyed by pull request number.
     */
    private final Map<Integer, PullRequest> pullRequestCache = new ConcurrentHashMap<>();

    @Inject
    public GitHubService(
            @ConfigProperty(name = "backports.token") String token,
            @ConfigProperty(name = "backports.repository") String repositoryName,
            @ConfigProperty(name = "backports.label") String backportLabel) {
        this.token = "Bearer " + token;
        this.repository = Repository.fromString(repositoryName);
        this.backportLabel = backportLabel;
    }

    public void clearPullRequestCache() {
        pullRequestCache.clear();
    }

    public PullRequest getPullRequest(Integer number) {
        PullRequest pullRequest = pullRequestCache.get(number);
        if (pullRequest == null) {
            throw new IllegalArgumentException("Something is wrong: we could not find pull request in the cache: #" + number);
        }
        return pullRequest;
    }

    public String getPullRequestsForBackportLabelUrl() {
        return String.format(PULL_REQUESTS_FOR_BACKPORT_LABEL_URL, repository.fullName(), backportLabel);
    }

    public String getOpenPullRequestsTargetingBranchUrl(Milestone milestone) {
        return String.format(OPEN_PULL_REQUESTS_TARGETING_BRANCH_LABEL_URL, repository.fullName(), milestone.minorVersion());
    }

    public String getMergedPullRequestsWithNoMilestoneUrl(Milestone milestone) {
        return String.format(MERGED_PULL_REQUESTS_TARGETING_BRANCH_WITH_NO_MILESTONE_LABEL_URL, repository.fullName(),
                milestone.minorVersion());
    }

    public ProjectV2 prepareRequirements(Milestone newMilestone) {
        String ownerId = getOwnerId(repository.owner());
        String repositoryId = getRepositoryId(repository);
        ProjectV2 projectV2 = getOrCreateProjectV2(ownerId, repositoryId, repository, newMilestone.minorVersion());
        return projectV2;
    }

    @PostConstruct
    void fetchBackportLabelID() {
        final String query = Templates.findBackportLabelId(repository.owner(), repository.name(), backportLabel).render();
        final JsonObject response = graphQLClient.graphql(token, new JsonObject().put("query", query));
        // Any errors?
        if (response.getJsonArray("errors") != null) {
            throw new RuntimeException(response.toString());
        }

        JsonObject backportLabelFromResponse = response.getJsonObject("data").getJsonObject("repository")
                .getJsonObject("label");

        if (backportLabelFromResponse == null) {
            throw new IllegalStateException(
                    "Backport label " + backportLabel + " could not be found in repository " + repository);
        }

        this.backportLabelId = backportLabelFromResponse.getString("id");
    }

    @CacheResult(cacheName = CacheNames.MILESTONES_CACHE_NAME)
    public Collection<Milestone> getOpenMilestones() throws IOException {
        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.listMilestones(repository.owner(), repository.name()).render()));
        // Any errors?
        if (response.getJsonArray("errors") != null) {
            throw new IOException(response.toString());
        }
        List<Milestone> milestoneList = new ArrayList<>();
        JsonArray milestones = response.getJsonObject("data")
                .getJsonObject("repository")
                .getJsonObject("milestones").getJsonArray("nodes");
        for (int i = 0; i < milestones.size(); i++) {
            JsonObject milestoneJsonObject = milestones.getJsonObject(i);
            String version = milestoneJsonObject.getString("title");

            if (!MICRO_VERSION_PATTERN.matcher(version).matches()) {
                continue;
            }

            milestoneList.add(new Milestone(milestoneJsonObject.getString("id"),
                    version,
                    getMinorVersion(version)));
        }

        milestoneList.sort(Comparator.comparing(m -> new ComparableVersion(m.title()), Comparator.reverseOrder()));

        return milestoneList;
    }

    @CacheResult(cacheName = CacheNames.PULL_REQUESTS_TO_BACKPORT_CACHE_NAME)
    public Collection<PullRequest> getBackportCandidatesPullRequests() throws IOException {
        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.listPullRequestsToBackport(repository.fullName(), backportLabel).render()));
        // Any errors?
        if (response.getJsonArray("errors") != null) {
            throw new IOException(response.toString());
        }

        return extractPullRequestsFromResponse(response);
    }

    public Collection<PullRequest> getOpenPullRequestsTargetingBranch(Milestone milestone) throws IOException {
        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query",
                        Templates.listOpenPullRequestsTargetingBranch(repository.fullName(), milestone.minorVersion())
                                .render()));
        // Any errors?
        if (response.getJsonArray("errors") != null) {
            throw new IOException(response.toString());
        }

        return extractPullRequestsFromResponse(response);
    }

    public Collection<PullRequest> getMergedPullRequestsTargetingBranchWithNoMilestone(Milestone milestone) throws IOException {
        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query",
                        Templates.listMergedPullRequestsTargetingBranchWithNoMilestone(repository.fullName(),
                                milestone.minorVersion()).render()));
        // Any errors?
        if (response.getJsonArray("errors") != null) {
            throw new IOException(response.toString());
        }

        // we will exclude the backport PRs from this list
        Pattern backportPrPattern = Pattern.compile(
                "^\\[" + Pattern.quote(milestone.minorVersion()) + "] " + Pattern.quote(milestone.title()) + " backport.*");

        return extractPullRequestsFromResponse(response).stream()
                .filter(pr -> !backportPrPattern.matcher(pr.title).matches())
                .toList();
    }

    private Collection<PullRequest> extractPullRequestsFromResponse(JsonObject response) throws IOException {
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
            pullRequest.merged = pr.getBoolean("merged");
            try {
                if (pr.getString("mergedAt") != null) {
                    pullRequest.mergedAt = sdf.parse(pr.getString("mergedAt"));
                }
            } catch (ParseException ignore) {
            }
            pullRequest.title = pr.getString("title");
            pullRequest.url = pr.getString("url");
            pullRequest.body = pr.getString("body");
            pullRequest.author = pr.getJsonObject("author").mapTo(User.class);
            pullRequest.commits = commitList;

            // Milestone
            JsonObject milestoneJson = pr.getJsonObject("milestone");
            if (milestoneJson != null) {
                pullRequest.milestone = milestoneJson.mapTo(Milestone.class);
            }

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
            // Extract missing issue numbers from the PR body
            Set<Integer> issueNumbers = issueExtractor.extractIssueNumbers(pullRequest.body)
                    .stream()
                    .filter(issueNumber -> issues.stream().noneMatch(issue -> issue.number == issueNumber))
                    .collect(Collectors.toSet());
            // Add missing issues to the linked issues list
            issues.addAll(findIssues(issueNumbers));
            pullRequest.linkedIssues = issues;
            prList.add(pullRequest);
        }

        for (PullRequest pr : prList) {
            pullRequestCache.put(pr.number, pr);
        }

        return prList;
    }

    public void markPullRequestAsBackported(PullRequest pullRequest, Milestone newMilestone) throws IOException {
        Milestone updatedMilestone;

        if (pullRequest.milestone != null &&
                new ComparableVersion(newMilestone.title())
                        .compareTo(new ComparableVersion(pullRequest.milestone.title())) > 0) {
            // if the PR milestone is already defined and is < to the new milestone, we keep it
            updatedMilestone = pullRequest.milestone;
        } else {
            updatedMilestone = newMilestone;
        }

        // Set Milestone and remove the backport tag
        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.updatePullRequest().render())
                .put("variables", new JsonObject()
                        .put("inputMilestone", new JsonObject()
                                .put("pullRequestId", pullRequest.id)
                                .put("milestoneId", updatedMilestone.id()))
                        .put("inputLabel", new JsonObject()
                                .put("labelableId", pullRequest.id)
                                .put("labelIds", backportLabelId))));
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
                                    .put("milestoneId", updatedMilestone.id()))
                            .put("inputLabel", new JsonObject()
                                    .put("labelableId", issue.id)
                                    .put("labelIds", backportLabelId))));
            // Any errors?
            if (response.getJsonArray("errors") != null) {
                throw new IOException(response.toString());
            }
        }

        // Add to ProjectV2 based on milestone version
        try {
            addIssueOrPullRequestToProjectV2(pullRequest.id, newMilestone.minorVersion(), newMilestone.title());

            for (Issue issue : pullRequest.linkedIssues) {
                addIssueOrPullRequestToProjectV2(issue.id, newMilestone.minorVersion(), newMilestone.title());
            }
        } catch (IOException e) {
            LOG.errorf(e, "Failed to add pull request: %s to project column", pullRequest.number);
        }
    }

    public void markPullRequestAsMerged(PullRequest pullRequest, Milestone newMilestone) throws IOException {
        // for now, let's do the exact same thing
        markPullRequestAsBackported(pullRequest, newMilestone);
    }

    public boolean isMilestonePresentInStatusField(String projectId, Milestone milestone) {
        ProjectV2Field statusField = getStatusField(projectId);

        return statusField.options.stream().anyMatch(o -> milestone.title().equals(o.name));
    }

    private Collection<Issue> findIssues(Collection<Integer> issueNumbers) throws IOException {
        if (issueNumbers.isEmpty()) {
            return Collections.emptySet();
        }
        String query = Templates.findIssues(repository.owner(), repository.name(), issueNumbers).render();
        JsonObject response = graphQLClient.graphql(token, new JsonObject().put("query", query));
        // Any errors?
        JsonArray errors = response.getJsonArray("errors");
        if (errors != null) {
            // Checking if there are any errors different from NOT_FOUND
            for (int k = 0; k < errors.size(); k++) {
                JsonObject error = errors.getJsonObject(k);
                if (!"NOT_FOUND".equals(error.getString("type"))) {
                    throw new IOException(error.toString());
                }
            }
        }
        Set<Issue> issues = new HashSet<>();
        JsonObject issuesJson = response.getJsonObject("data").getJsonObject("repository");
        for (Integer issueNumber : issueNumbers) {
            JsonObject jsonObject = issuesJson.getJsonObject("_" + issueNumber);
            // If the issue cannot be found, null is returned
            if (jsonObject != null && !jsonObject.isEmpty()) {
                issues.add(jsonObject.mapTo(Issue.class));
            }
        }
        return issues;
    }

    public void addIssueOrPullRequestToProjectV2(String itemId, String minorVersion, String microVersion) throws IOException {
        String repositoryId = getRepositoryId(repository);
        String ownerId = getOwnerId(repository.owner());

        ProjectV2 project = getOrCreateProjectV2(ownerId, repositoryId, repository, minorVersion);
        ProjectV2Field statusField = getStatusField(project.id);
        ProjectV2Item item = addItemToProjectV2(project.id, itemId);
        Optional<ProjectV2FieldOption> microVersionOption = statusField.options.stream()
                .filter(o -> microVersion.equals(o.name)).findFirst();

        if (microVersionOption.isEmpty()) {
            throw new IllegalStateException("Unable to find an option for micro version: " + microVersion);
        }

        updateProjectV2ItemFieldValue(project.id, item.id, statusField.id, microVersionOption.get().id);
    }

    private String getOwnerId(String owner) {
        return ownerIdCache.computeIfAbsent(owner, o -> {
            JsonObject variables = new JsonObject();
            variables.put("owner", owner);

            JsonObject response = graphQLClient.graphql(token, new JsonObject()
                    .put("query", Templates.getOwnerInfo().render()).put("variables", variables));

            if (response.getJsonArray("errors") != null &&
                    response.getJsonObject("data") == null) {
                throw new IllegalStateException("Unable to get owner info: " + response.getJsonArray("errors").toString());
            }
            JsonObject data = response.getJsonObject("data");
            JsonObject org = data.getJsonObject("organization");
            if (org != null) {
                return org.getString("id");
            }

            JsonObject user = data.getJsonObject("user");
            if (user != null) {
                return user.getString("id");
            }

            throw new IllegalStateException(String.format("Unable to get the owner information for owner: %s", owner));
        });
    }

    private String getRepositoryId(Repository repository) {
        return repositoryIdCache.computeIfAbsent(repository, r -> {
            JsonObject variables = new JsonObject();
            variables.put("owner", repository.owner());
            variables.put("name", repository.name());

            JsonObject response = graphQLClient.graphql(token, new JsonObject()
                    .put("query", Templates.getRepositoryInfo().render()).put("variables", variables));

            JsonObject data = response.getJsonObject("data");
            JsonObject repositoryNode = data.getJsonObject("repository");
            if (repositoryNode != null) {
                return repositoryNode.getString("id");
            }

            throw new IllegalStateException(
                    String.format("Unable to get the repository information for repository: %s", repository));
        });
    }

    // For now, we can't use this as we need a proper status of if the project has been already created or not
    @Deprecated
    private ProjectV2 getOrCreateProjectV2(String ownerId, String repositoryId, Repository repository, String minorVersion) {
        return projectCache.computeIfAbsent(minorVersion, mv -> {
            String projectTitle = String.format(PROJECT_NAME, mv);

            JsonObject variables = new JsonObject();
            variables.put("owner", repository.owner());
            variables.put("repository", repository.name());

            JsonObject response = graphQLClient.graphql(token, new JsonObject()
                    .put("query", Templates.getProjectsV2().render()).put("variables", variables));

            if (response.getJsonArray("errors") != null) {
                throw new IllegalStateException(String.format("Unable to get projects for repository: %s: %s",
                        repository.fullName(), response.getJsonArray("errors").toString()));
            }

            JsonObject data = response.getJsonObject("data");
            JsonArray projects = null;

            JsonObject repositoryNode = data.getJsonObject("repository");
            if (repositoryNode != null) {
                projects = repositoryNode.getJsonObject("projectsV2").getJsonArray("nodes");
            }

            ProjectV2 project = null;
            if (projects != null && !projects.isEmpty()) {
                for (int i = 0; i < projects.size(); i++) {
                    ProjectV2 projectCandidate = projects.getJsonObject(i).mapTo(ProjectV2.class);
                    if (projectTitle.equals(projectCandidate.title)) {
                        project = projectCandidate;
                        break;
                    }
                }
            }
            if (project == null) {
                project = createProjectV2(ownerId, repositoryId, minorVersion, projectTitle);
            }

            return project;
        });
    }

    private ProjectV2 createProjectV2(String ownerId, String repositoryId, String minorVersion, String title) {
        JsonObject variables = new JsonObject();
        variables.put("ownerId", ownerId);
        variables.put("repositoryId", repositoryId);
        variables.put("title", title);

        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.createProjectV2().render()).put("variables", variables));

        if (response.getJsonArray("errors") != null) {
            throw new IllegalStateException(String.format("Unable to create project for owner: %s, title: %s: %s",
                    repository.owner(), title, response.getJsonArray("errors").toString()));
        }

        ProjectV2 projectV2 = response.getJsonObject("data")
                .getJsonObject("createProjectV2")
                .getJsonObject("projectV2")
                .mapTo(ProjectV2.class);

        initializeProjectV2(projectV2.id, minorVersion);

        return projectV2;
    }

    private ProjectV2Field getStatusField(String projectId) {
        return statusFieldCache.computeIfAbsent(projectId, pi -> getProjectV2FieldOptions(projectId, STATUS_FIELD));
    }

    public ProjectV2Field refreshStatusField(String projectId) {
        statusFieldCache.remove(projectId);
        return statusFieldCache.computeIfAbsent(projectId, pi -> getProjectV2FieldOptions(projectId, STATUS_FIELD));
    }

    public String getStatusFieldSettingsUrl(Integer projectNumber) {
        return String.format(STATUS_FIELD_SETTINGS_URL, repository.owner(), projectNumber);
    }

    private ProjectV2Item addItemToProjectV2(String projectId, String contentId) {
        JsonObject variables = new JsonObject()
                .put("projectId", projectId)
                .put("contentId", contentId);

        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.addItemToProjectV2().render())
                .put("variables", variables));

        if (response.getJsonArray("errors") != null) {
            throw new IllegalStateException(String.format("Unable to add item: %s to project: %s. Errors: %s", "TODO item",
                    "TODO project", response.toString()));
        }

        return response.getJsonObject("data")
                .getJsonObject("addProjectV2ItemById")
                .getJsonObject("item")
                .mapTo(ProjectV2Item.class);
    }

    private void updateProjectV2ItemFieldValue(String projectId, String itemId, String fieldId, String optionId) {
        JsonObject variables = new JsonObject()
                .put("projectId", projectId)
                .put("itemId", itemId)
                .put("fieldId", fieldId)
                .put("optionId", optionId);

        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.updateProjectV2ItemFieldValue().render())
                .put("variables", variables));

        if (response.getJsonArray("errors") != null) {
            throw new IllegalStateException(String.format("Unable to update item field value: %s", response.toString()));
        }
    }

    public ProjectV2Field initializeProjectV2(String projectId, String minorVersion) {
        // Get current field options
        ProjectV2Field statusField = getProjectV2FieldOptions(projectId, STATUS_FIELD);

        // Prepare options list with the new option
        List<JsonObject> options = new ArrayList<>();

        // Add one option for each micro
        for (int i = 0; i < 9; i++) {
            String microVersion = minorVersion + "." + i;

            JsonObject newOptionJson = new JsonObject();
            newOptionJson.put("name", microVersion);
            newOptionJson.put("description", String.format(OPTION_DESCRIPTION, microVersion));
            newOptionJson.put("color", COLUMN_COLOR);
            options.add(newOptionJson);
        }

        // Update the field with new options
        ProjectV2Field projectV2Field = updateProjectV2FieldOptions(projectId, statusField.id, statusField.name,
                new JsonArray(options));
        // and make sure the cache is updated
        statusFieldCache.put(projectId, projectV2Field);

        return projectV2Field;
    }

    /**
     * Adds a new option to a ProjectV2 single select field.
     * This method retrieves all current options, adds the new option, and updates the field.
     *
     * @param projectId The ID of the ProjectV2
     * @param microVersion The name of the new option to add
     * @param newOptionColor The color of the new option (optional, can be null)
     * @return The updated ProjectV2Field with the new option
     *
     * @deprecated don't use this method for now as GitHub GraphQL API for single select field doesn't allow to update the field
     *             options...
     */
    @Deprecated
    public ProjectV2Field addOptionToStatusFieldIfNecessary(String projectId, String microVersion, String newOptionDescription,
            String newOptionColor) {
        // Get current field options
        ProjectV2Field statusField = getProjectV2FieldOptions(projectId, STATUS_FIELD);

        // Check if option already exists
        boolean optionExists = statusField.options.stream()
                .anyMatch(option -> microVersion.equals(option.name));

        if (optionExists) {
            LOG.debugf("Option '%s' already exists in field '%s'", microVersion, statusField.name);
            return statusField;
        }

        // Prepare options list with the new option
        List<JsonObject> options = new ArrayList<>();

        // Add existing options
        for (ProjectV2FieldOption option : statusField.options) {
            // drop all the standard options
            if (!MICRO_VERSION_PATTERN.matcher(option.name).matches()) {
                continue;
            }

            JsonObject optionJson = new JsonObject()
                    .put("name", option.name);
            optionJson.put("description", option.description);
            if (option.color != null && !option.color.trim().isEmpty()) {
                optionJson.put("color", option.color);
            }
            options.add(optionJson);
        }

        // Add new option
        JsonObject newOptionJson = new JsonObject();
        newOptionJson.put("name", microVersion);
        newOptionJson.put("description", newOptionDescription);
        if (newOptionColor != null && !newOptionColor.trim().isEmpty()) {
            newOptionJson.put("color", newOptionColor);
        }
        options.add(newOptionJson);

        options.sort(Comparator.comparing(o -> new ComparableVersion(o.getString("name"))));

        // Update the field with new options
        ProjectV2Field projectV2Field = updateProjectV2FieldOptions(projectId, statusField.id, statusField.name,
                new JsonArray(options));
        // and make sure the cache is updated
        statusFieldCache.put(projectId, projectV2Field);

        return projectV2Field;
    }

    /**
     * Retrieves the options for a specific ProjectV2 single select field.
     *
     * @param projectId The ID of the ProjectV2
     * @param fieldName The ID of the field
     * @return The ProjectV2Field with its options
     */
    private ProjectV2Field getProjectV2FieldOptions(String projectId, String fieldName) {
        JsonObject variables = new JsonObject()
                .put("projectId", projectId)
                .put("fieldName", fieldName);

        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.getProjectV2FieldOptions().render())
                .put("variables", variables));

        if (response.getJsonArray("errors") != null) {
            throw new IllegalStateException(String.format("Unable to get field options: %s", response.toString()));
        }

        JsonObject fieldData = response.getJsonObject("data")
                .getJsonObject("node")
                .getJsonObject("field");

        if (fieldData == null) {
            throw new IllegalArgumentException(String.format("Field %s not found in project %s", fieldName, projectId));
        }

        ProjectV2Field statusField = fieldData.mapTo(ProjectV2Field.class);

        if (!"SINGLE_SELECT".equals(statusField.dataType)) {
            throw new IllegalArgumentException("Field is not a single select field");
        }

        return statusField;
    }

    /**
     * Updates a ProjectV2 field with new options.
     *
     * @param projectId The ID of the ProjectV2
     * @param fieldId The ID of the field to update
     * @param fieldName The name of the field
     * @param options The new options for the field
     * @return The updated ProjectV2Field
     */
    private ProjectV2Field updateProjectV2FieldOptions(String projectId, String fieldId, String fieldName, JsonArray options) {
        JsonObject variables = new JsonObject()
                .put("fieldId", fieldId)
                .put("name", fieldName)
                .put("options", options);

        JsonObject response = graphQLClient.graphql(token, new JsonObject()
                .put("query", Templates.updateProjectV2Field().render())
                .put("variables", variables));

        if (response.getJsonArray("errors") != null) {
            throw new IllegalStateException(String.format("Unable to update field options: %s", response.toString()));
        }

        JsonObject fieldData = response.getJsonObject("data")
                .getJsonObject("updateProjectV2Field")
                .getJsonObject("projectV2Field");

        return fieldData.mapTo(ProjectV2Field.class);
    }

    public static String getMinorVersion(String version) {
        if (!MICRO_VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException(
                    "Invalid version " + version + ". Versions should be [0-9]+.[0-9]+.[0-9]+(.[0-9]+)?");
        }

        String[] versionParts = version.split("\\.");
        return versionParts[0] + "." + versionParts[1];
    }

    @CheckedTemplate
    private static class Templates {
        /**
         * Returns all the milestones from the repository
         */
        public static native TemplateInstance listMilestones(String owner, String repo);

        /**
         * Returns the (closed?) pull requests that match the specified label
         */
        public static native TemplateInstance listPullRequestsToBackport(String repo, String label);

        /**
         * Returns the open pull requests targeting the specified branch
         */
        public static native TemplateInstance listOpenPullRequestsTargetingBranch(String repo, String branch);

        /**
         * Returns the open pull requests targeting the specified branch
         */
        public static native TemplateInstance listMergedPullRequestsTargetingBranchWithNoMilestone(String repo, String branch);

        /**
         * Returns the backport label ID
         */
        public static native TemplateInstance findBackportLabelId(String owner, String repo, String label);

        /**
         * Returns the issues given their respective numbers
         */
        public static native TemplateInstance findIssues(String owner, String repo, Collection<Integer> issues);

        /**
         * Update the Pull Request to the specified milestone and remove backport label
         */
        public static native TemplateInstance updatePullRequest();

        /**
         * Update the Issue to the specified milestone and remove backport label
         */
        public static native TemplateInstance updateIssue();

        /**
         * Find ProjectV2
         */
        public static native TemplateInstance getProjectsV2();

        /**
         * Create a new ProjectV2
         */
        public static native TemplateInstance createProjectV2();

        /**
         * Add item to project
         */
        public static native TemplateInstance addItemToProjectV2();

        /**
         * Update project item field value
         */
        public static native TemplateInstance updateProjectV2ItemFieldValue();

        /**
         * Get owner information
         */
        public static native TemplateInstance getOwnerInfo();

        /**
         * Get owner information
         */
        public static native TemplateInstance getRepositoryInfo();

        /**
         * Get ProjectV2 field options
         */
        public static native TemplateInstance getProjectV2FieldOptions();

        /**
         * Update ProjectV2 field with new options
         */
        public static native TemplateInstance updateProjectV2Field();

    }

}
