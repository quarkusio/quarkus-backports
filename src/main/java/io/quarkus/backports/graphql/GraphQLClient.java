package io.quarkus.backports.graphql;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.HttpHeaders;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.vertx.core.json.JsonObject;

@RegisterRestClient(baseUri = "https://api.github.com/graphql")
public interface GraphQLClient {

    @POST
    JsonObject graphql(@HeaderParam(HttpHeaders.AUTHORIZATION) String authentication, JsonObject query);
}
