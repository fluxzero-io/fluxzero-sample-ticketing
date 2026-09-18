package io.fluxzero.ticketing.web;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.Message;
import io.fluxzero.sdk.web.*;
import io.fluxzero.ticketing.booking.BookingEndpoint;
import io.fluxzero.ticketing.catalog.CatalogEndpoint;
import io.fluxzero.ticketing.catalog.api.*;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.payment.stripe.CheckoutEndpoint;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TicketingEndpointTest extends TicketingTestSupport {
    private io.fluxzero.sdk.test.TestFixture browser(boolean async) {
        return fixture(async).registerHandlers(new CatalogEndpoint(), new BookingEndpoint(), new CheckoutEndpoint())
                .withProperty("fluxzero.auth.external-base-url", "http://localhost:8080");
    }
    private WebRequest mutation(String url, Object body) {
        return WebRequest.builder().url(url).method(HttpRequestMethod.POST).payload(body)
                .header("Origin", "http://localhost:8080").header("X-Ticketing-Request", "1").build();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void routesCatalogueFiltersDetailsAvailabilityAndSeats(boolean async) {
        browser(async).whenGet("/api/programme?city=Amsterdam&term=Night&month=2030-06")
                .expectWebResult(r -> r.<GetProgramme.Page>getPayloadAs(GetProgramme.Page.class).items().size()==2)
                .andThen().whenGet("/api/programme?city=Utrecht")
                .expectWebResult(r -> r.<GetProgramme.Page>getPayloadAs(GetProgramme.Page.class).items().size()==2)
                .andThen().whenGet("/api/programme?offset=3")
                .expectWebResult(r -> r.<GetProgramme.Page>getPayloadAs(GetProgramme.Page.class).items().size()==1)
                .andThen().whenGet("/api/programme/"+SHOW.getId())
                .expectWebResult(r -> r.<GetProgramme.Show>getPayloadAs(GetProgramme.Show.class).performance().performanceId().equals(SHOW))
                .andThen().whenGet("/api/programme/"+SHOW.getId()+"/availability")
                .expectWebResult(r -> r.<Availability>getPayloadAs(Availability.class).sections().getFirst().remaining()==4)
                .andThen().whenGet("/api/programme/"+SHOW.getId()+"/seats?section=stalls&offset=1")
                .expectWebResult(r -> r.<SeatPage>getPayloadAs(SeatPage.class).seats().getFirst().seat().id().equals("A2"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void routesOwnedReservationAndRelease(boolean async) {
        browser(async).whenWebRequestByUser(ALICE, mutation("/api/reservations", seats(R,"A1")))
                .expectWebResult(r -> r.getStatus()==200).expectNoErrors()
                .andThen().whenGetByUser(ALICE,"/api/reservations")
                .expectWebResult(r -> r.<GetMyReservations.Page>getPayloadAs(GetMyReservations.Page.class).items().size()==1)
                .andThen().whenGetByUser(BOB,"/api/reservations")
                .expectWebResult(r -> r.<GetMyReservations.Page>getPayloadAs(GetMyReservations.Page.class).items().isEmpty())
                .andThen().whenGetByUser(ALICE,"/api/reservations/"+R.getId())
                .expectWebResult(r -> r.<Purchase>getPayloadAs(Purchase.class).reservation().customerId().equals("alice"))
                .andThen().whenWebRequestByUser(ALICE,mutation("/api/reservations/"+R.getId()+"/cancel",null))
                .expectWebResult(r -> r.getStatus()==204)
                .expectThat(f -> assertEquals(ReservationStatus.CANCELLED, reservation().status()));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void refusesForeignReservationAndCrossOriginMutation(boolean async) {
        browser(async).givenCommandsByUser(ALICE,seats(R,"A1"))
                .whenGetByUser(BOB,"/api/reservations/"+R.getId())
                .expectWebResponse(r -> r.getStatus() == 401)
                .andThen().whenWebRequestByUser(ALICE,WebRequest.builder().url("/api/reservations/"+R.getId()+"/cancel")
                        .method(HttpRequestMethod.POST).header("Origin","https://untrusted.example")
                        .header("X-Ticketing-Request","1").build())
                .expectWebResponse(r -> r.getStatus() == 401)
                .expectThat(f -> assertEquals(ReservationStatus.HELD,reservation().status()));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void exposesUnavailableCheckoutWithoutCreatingPayment(boolean async) {
        browser(async).givenCommandsByUser(ALICE,seats(R,"A1"))
                .whenGet("/api/checkout/configuration")
                .expectWebResult(r -> !r.<CheckoutEndpoint.Configuration>getPayloadAs(CheckoutEndpoint.Configuration.class).available())
                .andThen().whenWebRequestByUser(ALICE,mutation("/api/checkout/"+R.getId(),null))
                .expectWebResponse(r -> r.getStatus() == 403)
                .expectThat(f -> assertTrue(Fluxzero.loadGraph(R).childModels(io.fluxzero.ticketing.payment.api.model.Payment.class).isEmpty()));
    }
}
