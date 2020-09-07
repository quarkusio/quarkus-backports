#! /bin/bash

export BACKPORTS_REPOSITORY="quarkusio/quarkus"
export BACKPORTS_LABEL="triage/backport-1.7?"

./mvnw clean quarkus:dev
