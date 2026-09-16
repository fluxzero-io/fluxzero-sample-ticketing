package io.fluxzero.ticketing.domain;

import io.fluxzero.sdk.modeling.*;
import lombok.With;
import java.time.*;
import java.util.*;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;

/** An independently identified room with an immutable demonstrator layout. */
@Model
@With
public record Hall(@EntityId HallId hallId,
                   @Parent(pathInParent = "halls", deleteOnParentDeletion = false) VenueId venueId,
                   HallDetails details) {

}
