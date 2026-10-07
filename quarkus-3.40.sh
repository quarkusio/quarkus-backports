#! /bin/bash

export BACKPORTS_REPOSITORY="quarkusio/quarkus"
export BACKPORTS_LABEL="triage/backport-3.40"

./mvnw clean quarkus:dev
