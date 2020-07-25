package io.quarkus.backports.graphql;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;

import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(baseUri = "https://api.github.com/graphql")
public interface GraphQLClient {

    @POST
    JsonObject graphql(@HeaderParam("Authorization") String authentication, JsonObject query);
}
