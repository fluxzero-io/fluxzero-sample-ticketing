package io.fluxzero.ticketing.catalog.privateapi;

import io.fluxzero.sdk.publishing.routing.RoutingKey;
import io.fluxzero.ticketing.catalog.api.PerformanceId;

/** Durable request to settle at most one page of purchases. */
public record SettlePerformanceCancellation(@RoutingKey PerformanceId performanceId) {}
