package io.example.bank;

import java.util.Random;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.bank.WithdrawalRedLeafEntity.DepositSeekEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = WithdrawalRedLeafEntity.class, ignoreUnknown = true)
public class WithdrawalRedLeafToDepositUnitAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(WithdrawalRedLeafToDepositUnitAction.class);
  private static final Random random = new Random();
  private final ComponentClient componentClient;

  public WithdrawalRedLeafToDepositUnitAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(WithdrawalRedLeafEntity.DepositSeekEvent event) {
    log.info("Event: {}", event);
    return effects().asyncReply(queryView(event));
  }

  private CompletionStage<String> queryView(DepositSeekEvent event) {
    return componentClient.forView()
        .call(DepositUnitsAvailableView::getDepositUnitsAvailable)
        .params(event.accountId())
        .execute()
        .thenCompose(queryResults -> processQueryResults(event, queryResults));
  }

  private CompletionStage<String> processQueryResults(DepositSeekEvent event, DepositUnitsAvailableView.DepositUnits queryReply) {
    var count = queryReply.depositUnits().size();

    if (count > 0) {
      var depositUnitRow = queryReply.depositUnits().get(random.nextInt(count));
      log.info("Found {} deposit units\n_event {}\n_attempt to withdraw from {}", count, event, depositUnitRow);
      return callFor(event, depositUnitRow);
    }

    log.info("No deposit units found\n_event {}", event);
    return callFor(event);
  }

  private CompletionStage<String> callFor(DepositSeekEvent event, DepositUnitsAvailableView.DepositUnitRow row) {
    var command = new DepositUnitEntity.WithdrawCommand(event.accountId(), event.withdrawalId(), event.leafId(), event.amountNeeded());
    return componentClient.forEventSourcedEntity(row.unitId())
        .call(DepositUnitEntity::withdraw)
        .params(command)
        .execute();
  }

  private CompletionStage<String> callFor(DepositSeekEvent event) {
    var command = new WithdrawalRedLeafEntity.CancelWithdrawalCommand(event.accountId(), event.withdrawalId(), event.leafId());
    return componentClient.forEventSourcedEntity(event.leafId())
        .call(WithdrawalRedLeafEntity::cancelWithdrawal)
        .params(command)
        .execute();
  }
}
