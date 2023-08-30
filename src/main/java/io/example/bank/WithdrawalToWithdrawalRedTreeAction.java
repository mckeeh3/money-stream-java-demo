package io.example.bank;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = WithdrawalEntity.class, ignoreUnknown = true)
public class WithdrawalToWithdrawalRedTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(WithdrawalToWithdrawalRedTreeAction.class);
  private final ComponentClient componentClient;

  public WithdrawalToWithdrawalRedTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(WithdrawalEntity.WithdrawalCreatedEvent event) {
    log.info("Event: {}", event);

    var branchId = UUID.randomUUID().toString();
    var withdrawalRedTreeId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId(event.withdrawalId().accountId(), event.withdrawalId().withdrawalId(), branchId);
    var command = new WithdrawalRedTreeEntity.TrunkCreateCommand(withdrawalRedTreeId, event.amount());

    return effects()
        .forward(componentClient.forEventSourcedEntity(withdrawalRedTreeId.toEntityId())
            .call(WithdrawalRedTreeEntity::createTrunk)
            .params(command));
  }
}
