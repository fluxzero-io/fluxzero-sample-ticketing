package io.fluxzero.ticketing.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.sdk.web.WebResponse;

/** Pure response validation; never performs I/O or maps uncertainty to payment failure. */
public final class ExternalResponse {
    private ExternalResponse() {}
    public static JsonNode json(WebResponse response) {
        if (response.getStatus() == null || response.getStatus() < 200 || response.getStatus() >= 300)
            throw new IntegrationFailure("External service returned HTTP " + response.getStatus());
        try {
            JsonNode result = response.getPayloadAs(JsonNode.class);
            if (result == null || !result.isObject()) throw new IntegrationFailure("External response must be a JSON object");
            return result;
        } catch (IntegrationFailure e) { throw e; }
        catch (RuntimeException e) { throw new IntegrationFailure("Malformed external JSON response"); }
    }
    public static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
            throw new IntegrationFailure("External response is missing a valid " + field);
        return value.textValue();
    }
    public static long positiveAmount(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0)
            throw new IntegrationFailure("External response has an invalid amount");
        return value.longValue();
    }
}
