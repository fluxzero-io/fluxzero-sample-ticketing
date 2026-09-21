package io.fluxzero.ticketing.admission;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.web.*;
import io.fluxzero.ticketing.access.BrowserRequests;
import io.fluxzero.ticketing.admission.api.GetTicketPass;
import io.fluxzero.ticketing.admission.api.RedeemTicket;
import io.fluxzero.ticketing.admission.api.SetGateOpen;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import org.springframework.stereotype.Component;

@Component @RequiresUser @ApiDoc(security = "ticketingSession")
public class AdmissionEndpoint {
    @HandleGet("/api/tickets/{id}/pass")
    WebResponse pass(@PathParam("id") TicketId id) {
        return WebResponse.builder().payload(Fluxzero.queryAndWait(new GetTicketPass(id)))
                .header("Cache-Control", "no-store").build();
    }
    @HandleGet("/api/tickets/{id}/qr")
    WebResponse qr(@PathParam("id") TicketId id) throws com.google.zxing.WriterException {
        var pass = Fluxzero.queryAndWait(new GetTicketPass(id));
        var matrix = new com.google.zxing.qrcode.QRCodeWriter().encode(pass.credential(), com.google.zxing.BarcodeFormat.QR_CODE, 1, 1);
        var svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(matrix.getWidth()).append(' ').append(matrix.getHeight()).append("\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><g fill=\"black\">");
        for (int y = 0; y < matrix.getHeight(); y++) for (int x = 0; x < matrix.getWidth(); x++)
            if (matrix.get(x, y)) svg.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"1\" height=\"1\"/>");
        return WebResponse.builder().payload(svg.append("</g></svg>").toString())
                .header("Content-Type", "image/svg+xml").header("Cache-Control", "no-store").build();
    }
    @HandleGet("/api/tickets/{id}/download")
    WebResponse download(@PathParam("id") TicketId id) {
        return WebResponse.builder().payload(TicketPdf.render(Fluxzero.queryAndWait(new GetTicketPass(id))))
                .header("Content-Type", "application/pdf").header("Content-Disposition", "attachment; filename=\"ticket.pdf\"")
                .header("Cache-Control", "no-store").build();
    }
    @HandleGet("/api/staff/performances")
    WebResponse staff(@QueryParam("offset") Integer offset) {
        return WebResponse.builder().payload(Fluxzero.queryAndWait(
                        new io.fluxzero.ticketing.operations.api.GetStaffPerformances(offset == null ? 0 : offset)))
                .header("Cache-Control", "no-store").build();
    }
    @HandleGet("/api/admission/{id}")
    WebResponse desk(@PathParam("id") PerformanceId id) {
        return WebResponse.builder().payload(Fluxzero.queryAndWait(new io.fluxzero.ticketing.admission.api.GetAdmissionDesk(id)))
                .header("Cache-Control", "no-store").build();
    }
    public record Scan(String credential) {}
    @HandlePost("/api/admission/{id}/scan")
    void scan(@PathParam("id") PerformanceId id, Scan scan, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new RedeemTicket(id, scan.credential()));
    }
    public record GateState(boolean open) {}
    @HandlePost("/api/admission/{id}/gate")
    void gate(@PathParam("id") PerformanceId id, GateState gate, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new SetGateOpen(id, gate.open()));
    }
}
