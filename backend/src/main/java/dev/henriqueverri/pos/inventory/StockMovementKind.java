package dev.henriqueverri.pos.inventory;

public enum StockMovementKind {
  /** Paid order: negative quantity. */
  SALE,
  /** Refunded order: gives back what its sale took. */
  REFUND,
  /** Goods received: positive quantity. */
  RECEIPT,
  /** Correction after a count, loss or breakage: either sign, always with a reason. */
  ADJUSTMENT
}
