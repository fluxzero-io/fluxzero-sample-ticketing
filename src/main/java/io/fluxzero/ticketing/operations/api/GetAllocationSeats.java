package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.*;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.api.GetSeats;
import io.fluxzero.ticketing.booking.api.model.SeatPage;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.validation.constraints.*;
import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record GetAllocationSeats(@NotNull PerformanceId performanceId, @NotBlank String sectionId,
                                  @PositiveOrZero int offset) implements Request<SeatPage> {
    @HandleQuery SeatPage handle(User user) {
        StaffPermission.require(performanceId, user, Permission.MANAGE);
        var performance = Fluxzero.loadModel(performanceId).get();
        require(performance != null, "Unknown performance");
        return GetSeats.read(performance, sectionId, offset, 100,
                !performance.cancelled() && Fluxzero.currentTime().isBefore(performance.details().startsAt()));
    }
}
