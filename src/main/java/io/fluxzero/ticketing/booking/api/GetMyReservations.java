package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

@RequiresUser
public record GetMyReservations(@PositiveOrZero int offset) implements Request<GetMyReservations.Page> {
    @HandleQuery Page handle(User user) {
        var items = Fluxzero.search(Reservation.class).match(user.id(), true, "customerId")
                .sortBy("createdAt", true).skip(offset).fetch(21);
        return new Page(items.stream().limit(20).toList(), offset, items.size() > 20);
    }
    public record Page(List<Reservation> items, int offset, boolean hasMore) {}
}
