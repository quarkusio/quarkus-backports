#! /bin/bash

export BACKPORTS_REPOSITORY="quarkusio/quarkus"
export BACKPORTS_LABEL="triage/backport-2.2?"

./mvnw clean quarkus:dev
