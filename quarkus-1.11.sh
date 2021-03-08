#! /bin/bash

export BACKPORTS_REPOSITORY="quarkusio/quarkus"
export BACKPORTS_LABEL="triage/backport-1.11?"

./mvnw clean quarkus:dev
