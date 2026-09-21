package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.operations.api.model.StaffAccess;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class StaffAccessId extends Id<StaffAccess> {
    public StaffAccessId(String value) { super(value, "staff-access-"); }
    public static StaffAccessId of(PerformanceId performanceId, String subject) {
        return new StaffAccessId(performanceId + ":" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(subject.getBytes(StandardCharsets.UTF_8)));
    }
}
