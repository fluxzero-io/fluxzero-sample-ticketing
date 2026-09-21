package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.operations.api.model.StaffAccess;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Trusted administration only. An empty permission set revokes access without erasing history. */
@RequiresAnyRole("OPERATOR")
public record SetStaffAccess(@NotNull PerformanceId performanceId, @NotBlank @Size(max = 255) String subject,
                             @NotNull Set<StaffAccess.Permission> permissions) {
    public StaffAccessId staffAccessId() { return StaffAccessId.of(performanceId, subject); }
    @Apply StaffAccess apply(Performance performance, @Nullable StaffAccess current) {
        return permissions.isEmpty() ? null
                : new StaffAccess(staffAccessId(), performanceId, subject, Set.copyOf(permissions));
    }
}
