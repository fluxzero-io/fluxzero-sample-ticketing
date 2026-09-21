package io.fluxzero.ticketing.wallet.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.ticketing.admission.api.GetTicketPass;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.wallet.ApplePass;
import jakarta.validation.constraints.NotNull;

@LocalOnly @RequiresUser
public record GetAppleWalletPass(@NotNull TicketId ticketId) implements Request<byte[]> {
    @HandleQuery byte[] handle() { return ApplePass.create(Fluxzero.queryAndWait(new GetTicketPass(ticketId))); }
}
