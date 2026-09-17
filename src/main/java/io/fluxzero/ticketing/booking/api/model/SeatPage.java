package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.ticketing.catalog.api.model.Seat;
import java.util.List;

/** A stable layout page includes unavailable seats so the UI preserves their positions. */
public record SeatPage(List<SeatChoice> seats, int offset, int total, boolean hasMore) {
    public record SeatChoice(Seat seat, boolean available) {}
}
