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

@ViewId("withdrawalReductionLeafView")
@Table("withdrawal_reduction_leaf")
@Subscribe.EventSourcedEntity(value = WithdrawalReductionLeafEntity.class, ignoreUnknown = true)
public class WithdrawalReductionLeafView extends View<WithdrawalReductionLeafView.LeafRow> {
  private static final Logger log = LoggerFactory.getLogger(WithdrawalReductionLeafView.class);

  @GetMapping("/withdrawalReductionLeaves/{withdrawalId}")
  @Query("""
      SELECT * AS leaves
        FROM withdrawal_reduction_leaf
       WHERE withdrawalId = :withdrawalId
      """)
  public Leaves getLeaves(@PathVariable String withdrawalId) {
    return null;
  }

  @Override
  public LeafRow emptyState() {
    return LeafRow.emptyState();
  }

  public UpdateEffect<LeafRow> on(WithdrawalReductionLeafEntity.DepositSeekEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public record Leaves(List<LeafRow> leaves) {}

  public record LeafRow(String accountId, String withdrawalId, String leafId, BigDecimal amount, BigDecimal balance) {

    public static LeafRow emptyState() {
      return new LeafRow(null, null, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public LeafRow on(WithdrawalReductionLeafEntity.DepositSeekEvent event) {
      return new LeafRow(event.accountId(), event.withdrawalId(), event.leafId(), event.amountNeeded(), balance);
    }

    public LeafRow on(WithdrawalReductionLeafEntity.DepositFoundEvent event) {
      var amountToWithdraw = event.amountToWithdraw();
      var amountWithdrawn = event.depositUnits().stream()
          .map(WithdrawalReductionLeafEntity.DepositUnit::amount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      return new LeafRow(accountId, withdrawalId, leafId, amountToWithdraw, amountWithdrawn);
    }
  }
}
