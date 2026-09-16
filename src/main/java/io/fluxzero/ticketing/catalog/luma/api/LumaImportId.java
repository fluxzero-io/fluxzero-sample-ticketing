package io.fluxzero.ticketing.catalog.luma.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.catalog.luma.api.model.LumaImport;

public final class LumaImportId extends Id<LumaImport> {
    public LumaImportId(String value) { super(value, "luma-import-id-"); }
}
