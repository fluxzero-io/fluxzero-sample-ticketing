package io.fluxzero.ticketing.domain;

import io.fluxzero.sdk.modeling.*;
import lombok.With;
import java.time.*;
import java.util.*;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;

/** A payment attempt with retained capture/refund facts, independently of admission rights. */
@Model
@With
public record Payment(@EntityId PaymentId paymentId,
                      @Parent(pathInParent = "payments", deleteOnParentDeletion = false) ReservationId reservationId,
                      Money expected, PaymentStatus status,
                      @Alias(prefix = "capture:") String captureReference, Money captured,
                      Instant capturedAt, String failureReason,
                      @Alias(prefix = "refund:") String refundReference, Instant refundedAt) {

}
