package io.example.bank;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

@ViewId("depositUnitsAvailable")
@Table("deposit_units_available")
@Subscribe.EventSourcedEntity(value = DepositUnitEntity.class, ignoreUnknown = true)
public class DepositUnitsAvailableView extends View<DepositUnitsAvailableView.DepositUnitRow> {
  private static final Logger log = LoggerFactory.getLogger(DepositUnitsAvailableView.class);

  @GetMapping("/depositUnitsAvailable/{accountId}")
  @Query("""
      SELECT * AS depositUnits
        FROM deposit_units_available
       WHERE accountId = :accountId
         AND balance > 0
       Limit 100
      """)
  public DepositUnits getDepositUnitsAvailable(@PathVariable String accountId) {
    return null;
  }

  @Override
  public DepositUnitRow emptyState() {
    return DepositUnitRow.emptyState();
  }

  public UpdateEffect<DepositUnitRow> on(DepositUnitEntity.ModifiedAmountEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<DepositUnitRow> on(DepositUnitEntity.WithdrawnEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<DepositUnitRow> on(DepositUnitEntity.WithdrawalCancelledEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public record DepositUnits(List<DepositUnitRow> depositUnits) {}

  public record DepositUnitRow(String accountId, String depositId, String unitId, BigDecimal amount, BigDecimal balance) {

    public static DepositUnitRow emptyState() {
      return new DepositUnitRow(null, null, null, null, null);
    }

    public DepositUnitRow on(DepositUnitEntity.ModifiedAmountEvent event) {
      return new DepositUnitRow(event.depositUnitId().accountId(), event.depositUnitId().depositId(), event.depositUnitId().unitId(), event.amount(), event.amount());
    }

    public DepositUnitRow on(DepositUnitEntity.WithdrawalCancelledEvent event) {
      return new DepositUnitRow(accountId, depositId, unitId, amount, event.balance());
    }

    public DepositUnitRow on(DepositUnitEntity.WithdrawnEvent event) {
      return new DepositUnitRow(accountId, depositId, unitId, amount, event.depositUnit().balance());
    }

    String toEntityId() {
      return "%s_%s_%s".formatted(accountId, depositId, unitId);
    }
  }
}
