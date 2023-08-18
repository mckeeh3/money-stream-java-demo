package io.example.bank;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.bank.DepositUnitEntity.WithdrawalCancelCommand;
import io.example.bank.WithdrawalRedLeafEntity.CanceledWithdrawalEvent;
import io.example.bank.WithdrawalRedLeafEntity.DepositSeekEvent;
import io.example.bank.WithdrawalRedLeafEntity.DepositUnit;
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

  public Effect<String> on(WithdrawalRedLeafEntity.CanceledWithdrawalEvent event) {
    log.info("Event: {}", event);

    var results = event.depositUnits().stream()
        .map(depositUnit -> toCommand(event, depositUnit))
        .map(command -> callFor(command))
        .toList();

    return effects().asyncReply(waitForCallsToComplete(results));
  }

  private CompletionStage<String> queryView(DepositSeekEvent event) {
    return componentClient.forView()
        .call(DepositUnitsAvailableView::getDepositUnitsAvailable)
        .params(event.withdrawalRedLeafId().accountId())
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
    var command = new DepositUnitEntity.WithdrawCommand(event.withdrawalRedLeafId(), event.amountNeeded());
    return componentClient.forEventSourcedEntity(row.toEntityId())
        .call(DepositUnitEntity::withdraw)
        .params(command)
        .execute();
  }

  private CompletionStage<String> callFor(DepositSeekEvent event) {
    var command = new WithdrawalRedLeafEntity.NoDepositsAvailableCommand(event.withdrawalRedLeafId());
    return componentClient.forEventSourcedEntity(event.withdrawalRedLeafId().toEntityId())
        .call(WithdrawalRedLeafEntity::noDepositsAvailable)
        .params(command)
        .execute();
  }

  private WithdrawalCancelCommand toCommand(CanceledWithdrawalEvent event, DepositUnit depositUnit) {
    return new DepositUnitEntity.WithdrawalCancelCommand(depositUnit.depositUnitId(), event.withdrawalRedLeafId());
  }

  private CompletionStage<String> callFor(WithdrawalCancelCommand command) {
    return componentClient.forEventSourcedEntity(command.depositUnitId().toEntityId())
        .call(DepositUnitEntity::cancelWithdrawal)
        .params(command)
        .execute();
  }

  private CompletableFuture<String> waitForCallsToComplete(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
        .thenApply(__ -> "OK");
  }
}
