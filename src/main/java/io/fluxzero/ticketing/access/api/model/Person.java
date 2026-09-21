package io.fluxzero.ticketing.access.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.ModelPersistence;

/** Recognizable identity from a verified sign-in; names never grant authority. */
@Model(persistence = ModelPersistence.DOCUMENT)
public record Person(@EntityId(prefix = "person-") String subject, String name) {}
