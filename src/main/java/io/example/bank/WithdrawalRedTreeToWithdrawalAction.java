package io.example.bank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = WithdrawalRedTreeEntity.class, ignoreUnknown = true)
public class WithdrawalRedTreeToWithdrawalAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(WithdrawalRedTreeToWithdrawalAction.class);
  private final ComponentClient componentClient;

  public WithdrawalRedTreeToWithdrawalAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(WithdrawalRedTreeEntity.WithdrawalApprovedEvent event) {
    log.info("Event: {}", event);

    var withdrawalId = new WithdrawalEntity.WithdrawalId(event.withdrawalRedTreeId().accountId(), event.withdrawalRedTreeId().withdrawalId());
    var command = new WithdrawalEntity.WithdrawalApproveCommand(withdrawalId);

    return effects().forward(componentClient.forEventSourcedEntity(withdrawalId.toEntityId())
        .call(WithdrawalEntity::approve)
        .params(command));
  }

  public Effect<String> on(WithdrawalRedTreeEntity.CanceledWithdrawalEvent event) {
    log.info("Event: {}", event);

    var withdrawalId = new WithdrawalEntity.WithdrawalId(event.withdrawalRedTreeId().accountId(), event.withdrawalRedTreeId().withdrawalId());
    var command = new WithdrawalEntity.WithdrawalInsufficientFundsCommand(withdrawalId);

    return effects().forward(componentClient.forEventSourcedEntity(withdrawalId.toEntityId())
        .call(WithdrawalEntity::insufficientFunds)
        .params(command));
  }
}
