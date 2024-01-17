#! /bin/bash

export BACKPORTS_REPOSITORY="quarkusio/quarkus"
export BACKPORTS_LABEL="triage/backport"

./mvnw clean quarkus:dev
