package io.fluxzero.ticketing.access.privateapi;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.access.api.model.Person;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

@RequiresAnyRole("IDENTITY")
public record RecordSignedInPerson(@NotBlank String subject, @NotBlank String name) {
    @Apply Person apply(@Nullable Person current) {
        return new Person(subject, name);
    }
}
