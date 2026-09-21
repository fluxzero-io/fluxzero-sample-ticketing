package io.fluxzero.ticketing.booking;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.admission.api.model.CheckIn;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.booking.api.model.TicketStatus;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import static io.fluxzero.ticketing.common.Checks.require;

public final class TransferRules {
    private TransferRules() {}
    public static void transferable(Ticket ticket, Performance performance) {
        require(ticket.status() == TicketStatus.VALID && !performance.cancelled()
                && Fluxzero.currentTime().isBefore(performance.details().startsAt()), "Ticket is no longer transferable");
        require(Fluxzero.loadModel(ticket.ticketId(), CheckIn.class).get() == null, "An admitted ticket cannot be transferred");
    }
}
