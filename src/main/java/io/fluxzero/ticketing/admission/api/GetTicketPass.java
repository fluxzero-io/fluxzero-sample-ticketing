package io.fluxzero.ticketing.admission.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.admission.TicketCredentials;
import io.fluxzero.ticketing.admission.api.model.CheckIn;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.booking.api.model.TicketStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.ZoneId;
import static io.fluxzero.ticketing.common.Checks.require;

@LocalOnly @RequiresUser
public record GetTicketPass(@NotNull TicketId ticketId) implements Request<GetTicketPass.Pass> {
    public record Pass(Ticket ticket, String title, Instant startsAt, ZoneId timeZone, String hall,
                       String section, String seat, String credential, CheckIn checkIn) {}
    @HandleQuery Pass handle(User user) {
        Ticket ticket = Fluxzero.loadModel(ticketId).get();
        require(ticket != null, "Unknown ticket");
        if (!ticket.customerId().equals(user.id())) throw new UnauthorizedException("Ticket belongs to another customer");
        var performance = Fluxzero.loadModel(ticket.performanceId()).get();
        require(ticket.status() == TicketStatus.VALID && !performance.cancelled(), "Ticket is no longer valid");
        var plan = Fluxzero.loadModel(performance.seatingPlanId()).get();
        var hall = Fluxzero.loadModel(plan.hallId()).get();
        var event = Fluxzero.loadModel(performance.eventId()).get();
        var section = plan.details().sections().stream().filter(s -> s.id().equals(ticket.admission().sectionId())).findFirst().orElseThrow();
        var seat = section.seats().stream().filter(s -> s.id().equals(ticket.admission().seatId())).findFirst();
        return new Pass(ticket, event.details().title(), performance.details().startsAt(), performance.details().timeZone(),
                hall.details().name(), section.name(), seat.map(s -> "Row " + s.row() + " / Seat " + s.number()).orElse("General admission"),
                TicketCredentials.issue(ticket), Fluxzero.loadModel(ticketId, CheckIn.class).get());
    }
}
