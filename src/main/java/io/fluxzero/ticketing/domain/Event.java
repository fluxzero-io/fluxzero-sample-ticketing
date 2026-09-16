package io.fluxzero.ticketing.domain;

import io.fluxzero.sdk.modeling.*;
import lombok.With;
import java.time.*;
import java.util.*;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;

/** The programme identity shared by one or more performances. */
@Model
@With
public record Event(@EntityId EventId eventId, EventDetails details) {

}
