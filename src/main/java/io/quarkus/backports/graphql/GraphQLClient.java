package io.quarkus.backports.graphql;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.HttpHeaders;

import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(baseUri = "https://api.github.com/graphql")
public interface GraphQLClient {

    @POST
    JsonObject graphql(@HeaderParam(HttpHeaders.AUTHORIZATION) String authentication, JsonObject query);
}
