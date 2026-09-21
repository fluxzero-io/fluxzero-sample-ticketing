package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.*;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.api.model.TicketTransfer;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

@RequiresUser
public record GetIncomingTransfers(@PositiveOrZero int offset) implements Request<GetIncomingTransfers.Page> {
    public record Invitation(TicketTransfer transfer, io.fluxzero.ticketing.catalog.api.GetProgramme.Show show,
                             io.fluxzero.ticketing.booking.api.model.Admission admission) {}
    public record Page(List<Invitation> items, int offset, boolean hasMore) {}
    @HandleQuery Page handle(User user) {
        var items = Fluxzero.search(TicketTransfer.class).match(user.id(), true, "recipientId")
                .match(TicketTransfer.Status.OFFERED, true, "status").sortBy("expiresAt").sortBy("ticketId").skip(offset).fetch(21);
        return new Page(items.stream().limit(20).map(transfer -> {
            var ticket = Fluxzero.loadModel(transfer.ticketId()).get();
            return new Invitation(transfer, io.fluxzero.ticketing.catalog.api.GetProgramme.describe(
                    Fluxzero.loadModel(ticket.performanceId()).get()), ticket.admission());
        }).toList(), offset, items.size() > 20);
    }
}
