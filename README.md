# Quarkus Backporting Application

The Quarkus project moves fast and when we prepare bugfix releases,
we usually have several dozens of pull requests to backport.

Using the GitHub UI to do that is feasible but cumbersome.

This small Quarkus application is dedicated to facilitate backporting.

## Usage

The index page allows to choose a milestone to backport to:

> ![Index Page](/documentation/screenshots/index.png?raw=true "Index Page")

Then the backports page lists all the pull requests marked with the backport label:

> ![Backports Page](/documentation/screenshots/backports.png?raw=true "Index Page")

You can paste the `git cherry-pick` command and execute it in a terminal.

Finally, you mark the backport of this pull request as done:

 * It removes the backport label from the pull request.
 * It affects the milestone to backport to to the pull request.

## Setup

In your application directory, create a .env file containing your OAuth token:

```
BACKPORTS_TOKEN=<TOKEN>
```

Obviously, this token needs write permissions for the repository.

For testing, you obviously don't want to use the Quarkus repository.
You can easily target another repository with:

```
export BACKPORTS_REPOSITORY=my/repository
```

By default, the label we use to mark the pull requests to backport is `triage/backport?`.
You can easily customize it in the `application.properties` or use:

```
export BACKPORTS_LABEL=my-label
```

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```
./mvnw quarkus:dev
```

## Packaging and running the application

The application can be packaged using `./mvnw package`.
It produces the `quarkus-backports-1.0-SNAPSHOT-runner.jar` file in the `/target` directory.

The application is now runnable using `java -jar target/quarkus-backports-1.0-SNAPSHOT-runner.jar`.

## Testing the GraphQL

If for any reason you need to change the GraphQL used to query the GitHub API, use https://developer.github.com/v4/explorer/ to test it.
Don't forget to update the changed output in the `documentation/graphql` directory