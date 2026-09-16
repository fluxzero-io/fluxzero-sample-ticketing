package io.fluxzero.ticketing.domain;

import io.fluxzero.sdk.modeling.*;
import lombok.With;
import java.time.*;
import java.util.*;
import static io.fluxzero.common.api.modeling.ModelConflictPolicy.RETRY;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;

/** One dated occurrence, with frozen layout and prices for reliable ticket identity. */
@Model(conflictPolicy = RETRY)
@With
public record Performance(@EntityId PerformanceId performanceId,
                          @Parent(pathInParent = "performances", deleteOnParentDeletion = false) EventId eventId,
                          @Parent(pathInParent = "performances", deleteOnParentDeletion = false) HallId hallId,
                          PerformanceDetails details, HallDetails layout, boolean cancelled) {

}
