package io.fluxzero.ticketing.access;

import io.fluxzero.sdk.common.HasMessage;
import io.fluxzero.sdk.tracking.handling.authentication.AbstractUserProvider;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.access.api.model.TicketingUser;
import org.springframework.stereotype.Component;

@Component
public class TicketingUserProvider extends AbstractUserProvider {
    public TicketingUserProvider() { super(TicketingUser.class); }
    @Override public User fromMessage(HasMessage message) {
        User trusted = super.fromMessage(message);
        if (trusted != null) return trusted;
        if (!(message.toMessage() instanceof WebRequest)) return null;
        return BrowserSessions.find(message.getMetadata()).map(s -> getUserById(s.subject())).orElse(null);
    }
    @Override public TicketingUser getUserById(Object id) {
        return id == null ? null : new TicketingUser(id.toString());
    }
    @Override public User getSystemUser() { return TicketingUser.SYSTEM; }
}
