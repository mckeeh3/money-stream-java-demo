package io.example.bank;

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

  public Effect<String> on(WithdrawalEntity.WithdrawnEvent event) {
    log.info("Event: {}", event);

    var command = new WithdrawalRedTreeEntity.TrunkCreateCommand(event.accountId(), event.withdrawalId(), event.amount());

    return effects()
        .forward(componentClient.forEventSourcedEntity(event.accountId())
            .call(WithdrawalRedTreeEntity::createTrunk)
            .params(command));
  }
}