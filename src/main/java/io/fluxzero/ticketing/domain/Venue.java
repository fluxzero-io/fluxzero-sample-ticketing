package io.fluxzero.ticketing.domain;

import io.fluxzero.sdk.modeling.*;
import lombok.With;
import java.time.*;
import java.util.*;
import static io.fluxzero.common.api.modeling.ModelConflictPolicy.RETRY;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;

/** A real venue with sourced descriptive data. */
@Model(conflictPolicy = RETRY)
@With
public record Venue(@EntityId VenueId venueId, VenueDetails details) {

}
