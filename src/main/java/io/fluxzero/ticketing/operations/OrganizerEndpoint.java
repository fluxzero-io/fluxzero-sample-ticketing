package io.fluxzero.ticketing.operations;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.web.ApiDoc;
import io.fluxzero.sdk.web.ApiDocResponse;
import io.fluxzero.sdk.web.HandleGet;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.Path;
import io.fluxzero.sdk.web.PathParam;
import io.fluxzero.sdk.web.QueryParam;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.sdk.web.WebResponse;
import io.fluxzero.ticketing.access.BrowserRequests;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.catalog.api.CancelPerformance;
import io.fluxzero.ticketing.catalog.api.ConfigureSalesWindow;
import io.fluxzero.ticketing.catalog.api.EventId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.SchedulePerformance;
import io.fluxzero.ticketing.catalog.api.SeatingPlanId;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.PerformanceDetails;
import io.fluxzero.ticketing.operations.api.CancelManagedReservation;
import io.fluxzero.ticketing.operations.api.GetManagedOrders;
import io.fluxzero.ticketing.operations.api.GetManagedPerformance;
import io.fluxzero.ticketing.operations.api.GetManagedPerformances;
import io.fluxzero.ticketing.operations.api.GetManagedReservation;
import io.fluxzero.ticketing.operations.api.GetOrganizerCatalog;
import io.fluxzero.ticketing.operations.api.SetStaffAccess;
import io.fluxzero.ticketing.operations.api.GetStaffDirectory;
import io.fluxzero.ticketing.operations.api.GetWorkspaceAccess;
import io.fluxzero.ticketing.operations.api.RecoverManagedRefund;
import io.fluxzero.ticketing.operations.api.RetryManagedDelivery;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import io.fluxzero.ticketing.payment.api.model.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

import static io.fluxzero.ticketing.common.Checks.require;

@ApiDoc(security = "ticketingSession")
@Component
@RequiresUser
@Path("/api/operations")
public class OrganizerEndpoint {
    @ApiDocResponse(status = 200, type = GetManagedPerformances.Page.class)
    @HandleGet("/performances") WebResponse performances(@QueryParam("offset") Integer offset) {
        return response(Fluxzero.queryAndWait(new GetManagedPerformances(offset == null ? 0 : offset)));
    }

    @HandleGet("/catalog") WebResponse catalog(@QueryParam("eventOffset") Integer eventOffset,
                                              @QueryParam("planOffset") Integer planOffset) {
        return response(Fluxzero.queryAndWait(new GetOrganizerCatalog(
                eventOffset == null ? 0 : eventOffset, planOffset == null ? 0 : planOffset)));
    }

    @HandleGet("/performances/{id}") WebResponse performance(@PathParam("id") PerformanceId id) {
        return response(Fluxzero.queryAndWait(new GetManagedPerformance(id)));
    }

    public record Schedule(@NotBlank String performanceId, @NotBlank String eventId,
                           @NotBlank String seatingPlanId, @NotNull LocalDateTime startsAt,
                           @NotEmpty Map<@NotBlank String, @NotNull @Valid Money> sectionPrices) {}

    @HandlePost("/performances") PerformanceId schedule(Schedule schedule, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        var plan = Fluxzero.loadModel(new SeatingPlanId(schedule.seatingPlanId())).get();
        require(plan != null, "Unknown seating plan");
        var hall = Fluxzero.loadModel(plan.hallId()).get();
        var venue = Fluxzero.loadModel(hall.venueId()).get();
        var zone = venue.details().timeZone();
        require(zone.getRules().getValidOffsets(schedule.startsAt()).size() == 1,
                "Choose an unambiguous local time outside the daylight-saving clock change");
        var command = new SchedulePerformance(new PerformanceId(schedule.performanceId()),
                new EventId(schedule.eventId()), new SeatingPlanId(schedule.seatingPlanId()),
                new PerformanceDetails(schedule.startsAt().atZone(zone).toInstant(), zone, schedule.sectionPrices()));
        Fluxzero.sendCommandAndWait(command);
        return command.performanceId();
    }

    public record Window(@NotNull LocalDateTime opensAt, @NotNull LocalDateTime closesAt) {}

    @HandlePost("/performances/{id}/sales-window") void salesWindow(
            @PathParam("id") PerformanceId id, Window window, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Performance performance = Fluxzero.loadModel(id).get();
        require(performance != null, "Unknown performance");
        var zone = performance.details().timeZone();
        require(zone.getRules().getValidOffsets(window.opensAt()).size() == 1
                        && zone.getRules().getValidOffsets(window.closesAt()).size() == 1,
                "Choose unambiguous local times outside the daylight-saving clock change");
        Fluxzero.sendCommandAndWait(new ConfigureSalesWindow(id,
                window.opensAt().atZone(zone).toInstant(), window.closesAt().atZone(zone).toInstant()));
    }

    @HandlePost("/performances/{id}/cancel") void cancelPerformance(
            @PathParam("id") PerformanceId id, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new CancelPerformance(id));
    }

    @HandleGet("/performances/{id}/orders") WebResponse orders(
            @PathParam("id") PerformanceId id, @QueryParam("term") String term,
            @QueryParam("status") String status, @QueryParam("offset") Integer offset) {
        return response(Fluxzero.queryAndWait(new GetManagedOrders(id, term, status(status), offset == null ? 0 : offset)));
    }

    @ApiDocResponse(status = 200, type = GetManagedReservation.View.class)
    @HandleGet("/orders/{id}") WebResponse order(@PathParam("id") ReservationId id,
                                                @QueryParam("offset") Integer offset) {
        return response(Fluxzero.queryAndWait(new GetManagedReservation(id, offset == null ? 0 : offset)));
    }

    @HandlePost("/orders/{id}/cancel") void cancelOrder(@PathParam("id") ReservationId id, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new CancelManagedReservation(id));
    }

    public record Access(@NotBlank String subject, @NotNull Set<Permission> permissions) {}

    @HandleGet("/access") WebResponse workspaceAccess() {
        return response(Fluxzero.queryAndWait(new GetWorkspaceAccess()));
    }

    @HandleGet("/performances/{id}/staff") WebResponse staff(@PathParam("id") PerformanceId id,
            @QueryParam("assigned") Boolean assigned, @QueryParam("term") String term,
            @QueryParam("offset") Integer offset) {
        return response(Fluxzero.queryAndWait(new GetStaffDirectory(id, !Boolean.FALSE.equals(assigned), term,
                offset == null ? 0 : offset)));
    }

    public record RefundAction(@NotBlank String attemptId) {}

    @HandlePost("/payments/{id}/recover-refund") void refund(@PathParam("id") PaymentId id,
            RefundAction action, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new RecoverManagedRefund(id, action.attemptId()));
    }

    @HandlePost("/orders/{id}/retry-delivery") void retryDelivery(@PathParam("id") ReservationId id,
                                                                WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new RetryManagedDelivery(id));
    }

    @HandlePost("/performances/{id}/access") void access(
            @PathParam("id") PerformanceId id, Access access, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new SetStaffAccess(id, access.subject(), access.permissions()));
    }

    private static WebResponse response(Object payload) {
        return WebResponse.builder().payload(payload).header("Cache-Control", "no-store").build();
    }

    private static ReservationStatus status(String value) {
        if (value == null || value.isBlank()) return null;
        try { return ReservationStatus.valueOf(value); }
        catch (IllegalArgumentException e) { throw new IllegalCommandException("Unknown order status"); }
    }
}
