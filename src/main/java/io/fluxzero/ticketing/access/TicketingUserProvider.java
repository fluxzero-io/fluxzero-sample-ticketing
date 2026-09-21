package io.fluxzero.ticketing.access;

import io.fluxzero.sdk.common.HasMessage;
import io.fluxzero.sdk.tracking.handling.authentication.AbstractUserProvider;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.access.api.model.TicketingUser;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;

import static io.fluxzero.sdk.configuration.ApplicationProperties.getProperty;

@Component
public class TicketingUserProvider extends AbstractUserProvider {
    public TicketingUserProvider() { super(TicketingUser.class); }
    @Override public User fromMessage(HasMessage message) {
        if (message.toMessage() instanceof WebRequest) {
            User browserUser = BrowserSessions.find(message.getMetadata())
                    .map(session -> getUserById(session.subject())).orElse(null);
            if (browserUser != null) return browserUser;
        }
        User trusted = super.fromMessage(message);
        if (trusted != null) return trusted;
        return null;
    }
    @Override public TicketingUser getUserById(Object id) {
        if (id == null) return null;
        String subject = id.toString();
        boolean operator = Arrays.stream(getProperty("ticketing.operator-subjects", "").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).anyMatch(subject::equals);
        return new TicketingUser(subject, operator ? Set.of("OPERATOR") : Set.of());
    }
    @Override public User getSystemUser() { return TicketingUser.SYSTEM; }
}
