package dev.henriqueverri.pos.order;

import dev.henriqueverri.pos.shared.error.ApiException;
import dev.henriqueverri.pos.shared.error.ErrorCodes;
import org.springframework.http.HttpStatus;

public class InvalidOrderTransitionException extends ApiException {

  public InvalidOrderTransitionException(OrderStatus from, OrderStatus to) {
    super(
        HttpStatus.CONFLICT,
        ErrorCodes.INVALID_ORDER_TRANSITION,
        "Pedido " + from + " não pode passar para " + to + ".");
  }
}
