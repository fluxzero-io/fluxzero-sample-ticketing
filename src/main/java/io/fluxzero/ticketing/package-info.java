@RegisterType
@ApiDocInfo(title = "Fluxzero Ticketing", version = "1.0.0",
        components = @ApiDocComponent(path = "securitySchemes.ticketingSession",
                json = "{\"type\":\"apiKey\",\"in\":\"cookie\",\"name\":\"ticketing_session\"}"),
        serveOpenApi = true, openApiPath = "/api/openapi.json",
        serveApiReference = true, apiReferencePath = "/api/docs")
package io.fluxzero.ticketing;

import io.fluxzero.common.serialization.RegisterType;
import io.fluxzero.sdk.web.ApiDocInfo;
import io.fluxzero.sdk.web.ApiDocComponent;
