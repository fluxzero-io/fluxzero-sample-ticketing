package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.*;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

@RequiresUser
public record GetOwnedTickets(@PositiveOrZero int offset) implements Request<GetOwnedTickets.Page> {
    public record Page(List<Ticket> items, int offset, boolean hasMore) {}
    @HandleQuery Page handle(User user) {
        var items = Fluxzero.search(Ticket.class).match(user.id(), true, "customerId").sortBy("ticketId").skip(offset).fetch(21);
        return new Page(items.stream().limit(20).toList(), offset, items.size() > 20);
    }
}
