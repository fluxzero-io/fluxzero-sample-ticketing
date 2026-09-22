package io.fluxzero.ticketing.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Locate the existing managed runtime; never start a second runtime from a test. */
public abstract class RuntimeTestSupport extends TicketingTestSupport {
    protected static String runtimeUrl() throws IOException {
        String url = ApplicationProperties.getProperty("ticketing.test.runtimeUrl");
        Path sessionFile = Path.of(".fluxzero/dev/session.json");
        if (url == null && Files.isRegularFile(sessionFile)) {
            var session = new ObjectMapper().readTree(sessionFile.toFile());
            if (ProcessHandle.of(session.path("pid").asLong()).filter(ProcessHandle::isAlive).isPresent())
                url = session.path("runtime").path("url").asText(null);
        }
        assumeTrue(url != null, "Requires fz dev or TICKETING_TEST_RUNTIMEURL");
        return url;
    }
}
