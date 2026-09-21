package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.access.api.model.Person;
import io.fluxzero.ticketing.booking.*;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.booking.privateapi.ReservationHeld;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import static io.fluxzero.ticketing.common.Checks.require;

/** Secure the customer's complete selection before the cashier takes money. */
@RequiresUser
public record ReserveBoxOfficeTickets(@NotNull ReservationId reservationId, @NotNull PerformanceId performanceId,
        @NotBlank String customerId, @NotEmpty @Size(max=12) List<@NotNull @Valid Selection> selection) {
    @InterceptApply Object decide(Performance performance, User user) {
        StaffPermission.assertForUser(performanceId,user,Permission.MANAGE);
        require(Fluxzero.loadModel(customerId, Person.class).get() != null,"Customer must sign in once to receive tickets");
        var now = Fluxzero.currentTime();
        ReservationRules.validSelection(performance,selection,now);
        var expires = now.plus(Duration.ofMinutes(15));
        if (expires.isAfter(performance.details().startsAt())) expires = performance.details().startsAt();
        expires = expires.truncatedTo(ChronoUnit.SECONDS);
        require(expires.isAfter(now),"Booking closes at performance start");
        var admissions = ReservationRules.admissions(performance,selection);
        var reservation = new Reservation(reservationId,performanceId,customerId,admissions,ReservationRules.total(admissions),
                now,expires,ReservationStatus.HELD,null,SalesChannel.BOX_OFFICE);
        var result = new ArrayList<Object>();
        result.add(new ReservationHeld(reservationId,reservation));
        result.addAll(InventoryChanges.hold(reservation,performance,now));
        return result;
    }
}
