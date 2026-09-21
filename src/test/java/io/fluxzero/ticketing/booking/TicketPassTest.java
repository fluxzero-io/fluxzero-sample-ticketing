package io.fluxzero.ticketing.booking;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.ticketing.admission.TicketPdf;
import io.fluxzero.ticketing.admission.api.GetTicketPass;
import io.fluxzero.ticketing.admission.api.RedeemTicket;
import io.fluxzero.ticketing.admission.api.SetGateOpen;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TicketPassTest extends TicketingTestSupport {
    private static final TicketId TICKET = new TicketId("alice-order:1");
    private static final String KEY = "fixture-only-admission-key-32-bytes-minimum";

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void malformedCodesAreFunctionalRejections(boolean async) {
        paid(async).withProperty("ticketing.admission.signing-key", KEY)
                .givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true))
                .whenCommandByUser(OPERATOR, new RedeemTicket(SHOW, "not-a-ticket"))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void onlyTheTicketOwnerCanObtainTheAdmissionCode(boolean async) {
        paid(async).withProperty("ticketing.admission.signing-key", KEY)
                .whenQueryByUser(BOB, new GetTicketPass(TICKET)).expectExceptionalResult(UnauthorizedException.class)
                .andThen().whenQueryByUser(ALICE, new GetTicketPass(TICKET))
                .expectResult((GetTicketPass.Pass p) -> p.ticket().ticketId().equals(TICKET) && p.credential() != null);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void downloadedPdfContainsTheSelectionAndAScannableCode(boolean async) {
        paid(async).withProperty("ticketing.admission.signing-key", KEY)
                .givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true))
                .whenExecuting(f -> {
                    var pass = ALICE.apply(() -> Fluxzero.queryAndWait(new GetTicketPass(TICKET)));
                    byte[] pdf = TicketPdf.render(new GetTicketPass.Pass(pass.ticket(),
                            "An unusually long concert title ".repeat(10) + "🎵", pass.startsAt(), pass.timeZone(),
                            "A venue\nwith a long name ".repeat(10), pass.section(), pass.seat(), pass.credential(), pass.checkIn()));
                    try (var document = Loader.loadPDF(pdf)) {
                        assertEquals(1, document.getNumberOfPages());
                        assertEquals(PDRectangle.A4.getWidth(), document.getPage(0).getMediaBox().getWidth());
                        assertEquals(PDRectangle.A4.getHeight(), document.getPage(0).getMediaBox().getHeight());
                        String text = new PDFTextStripper().getText(document);
                        assertTrue(text.contains("Row A / Seat 1"));
                        assertTrue(text.contains("Standard"));
                        assertTrue(text.contains("…"));
                        var image = new PDFRenderer(document).renderImageWithDPI(0, 120);
                        var source = new RGBLuminanceSource(image.getWidth(), image.getHeight(),
                                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
                        String decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
                        assertEquals(pass.credential(), decoded);
                        OPERATOR.apply(() -> Fluxzero.sendCommandAndWait(new RedeemTicket(SHOW, decoded)));
                    }
                }).expectSuccessfulResult().expectNoErrors();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aTamperedCodeCannotAdmitATicket(boolean async) {
        paid(async).withProperty("ticketing.admission.signing-key", KEY)
                .givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true))
                .whenExecuting(f -> {
                    var pass = ALICE.apply(() -> Fluxzero.queryAndWait(new GetTicketPass(TICKET)));
                    String forged = pass.credential().substring(0, pass.credential().length() - 2) + "xx";
                    assertThrows(IllegalCommandException.class, () -> OPERATOR.apply(() ->
                            Fluxzero.sendCommandAndWait(new RedeemTicket(SHOW, forged))));
                }).expectSuccessfulResult();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aDownloadedCodeDoesNotBypassLaterCancellation(boolean async) {
        paid(async).withProperty("ticketing.admission.signing-key", KEY)
                .givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true))
                .whenExecuting(f -> {
                    var pass = ALICE.apply(() -> Fluxzero.queryAndWait(new GetTicketPass(TICKET)));
                    ALICE.apply(() -> Fluxzero.sendCommandAndWait(new CancelReservation(R)));
                    assertThrows(IllegalCommandException.class, () -> OPERATOR.apply(() ->
                            Fluxzero.sendCommandAndWait(new RedeemTicket(SHOW, pass.credential()))));
                }).expectSuccessfulResult();
    }
}
