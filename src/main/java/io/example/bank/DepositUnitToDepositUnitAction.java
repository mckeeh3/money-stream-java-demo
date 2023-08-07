package io.example.bank;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = DepositUnitEntity.class, ignoreUnknown = true)
public class DepositUnitToDepositUnitAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(DepositUnitToDepositUnitAction.class);
  private final ComponentClient componentClient;

  public DepositUnitToDepositUnitAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(DepositUnitEntity.ModifiedAmountEvent event) {
    log.info("Event: {}", event);

    if (event.modifyAmounts().isEmpty()) {
      return effects().reply("OK");
    }

    var results = event.modifyAmounts().stream()
        .map(modifyAmount -> new DepositUnitEntity.ModifyAmountCommand(event.depositUnitId(), modifyAmount.amount()))
        .map(command -> componentClient.forEventSourcedEntity(command.depositUnitId().toEntityID())
            .call(DepositUnitEntity::modifyAmount)
            .params(command))
        .map(deferredCall -> deferredCall.execute())
        .toList();

    return effects().asyncReply(waitForCallsToComplete(results));
  }

  private CompletableFuture<String> waitForCallsToComplete(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
        .thenApply(__ -> "OK");
  }
}
