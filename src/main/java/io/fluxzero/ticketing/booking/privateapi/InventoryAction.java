package io.fluxzero.ticketing.booking.privateapi;

/** The admission transition committed atomically with its inventory. */
public enum InventoryAction {
    HOLD, SELL, RELEASE_HOLD, RELEASE_SALE;

    public int heldChange(int quantity) {
        return switch (this) { case HOLD -> quantity; case SELL, RELEASE_HOLD -> -quantity; case RELEASE_SALE -> 0; };
    }
    public int soldChange(int quantity) {
        return switch (this) { case SELL -> quantity; case RELEASE_SALE -> -quantity; default -> 0; };
    }
    public boolean releases() { return this == RELEASE_HOLD || this == RELEASE_SALE; }
}
