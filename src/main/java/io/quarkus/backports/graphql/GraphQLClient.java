package io.quarkus.backports.graphql;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.core.HttpHeaders;

import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(baseUri = "https://api.github.com/graphql")
public interface GraphQLClient {

    @POST
    JsonObject graphql(@HeaderParam(HttpHeaders.AUTHORIZATION) String authentication, JsonObject query);
}
