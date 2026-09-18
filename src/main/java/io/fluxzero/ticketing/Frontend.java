package io.fluxzero.ticketing;

import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.sdk.web.ServeStatic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Component;

/** Serves packaged assets. During development the managed gateway routes the UI to Vite. */
@Component
@ConditionalOnResource(resources = "classpath:/static/index.html")
@NoUserRequired
@ServeStatic(resourcePath = "classpath:/static", ignorePaths = {"/api/*", "/app/*", "/_fluxzero/*", "/login", "/oauth2/*", "/.well-known/*", "/userinfo"})
public class Frontend {}
