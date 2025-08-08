#! /bin/bash

export BACKPORTS_REPOSITORY="gsmet/test-backports"
export BACKPORTS_LABEL="triage/backport"

./mvnw clean quarkus:dev
