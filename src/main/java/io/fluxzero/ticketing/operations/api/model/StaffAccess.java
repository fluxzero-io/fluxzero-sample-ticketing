package io.fluxzero.ticketing.operations.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.ModelPersistence;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.operations.api.StaffAccessId;
import java.util.Set;

/** Rights for one verified identity at one performance; no provider or browser-supplied roles. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED, ModelPersistence.DOCUMENT})
public record StaffAccess(@EntityId StaffAccessId staffAccessId,
                          @Parent(pathInParent = "staff") PerformanceId performanceId,
                          String subject, Set<Permission> permissions) {
    public enum Permission { ADMISSION, MANAGE }
}
