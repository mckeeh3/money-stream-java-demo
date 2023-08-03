package io.example.bank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = WithdrawalEntity.class, ignoreUnknown = true)
public class WithdrawalToWithdrawalReductionTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(WithdrawalToWithdrawalReductionTreeAction.class);
  private final ComponentClient componentClient;

  public WithdrawalToWithdrawalReductionTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(WithdrawalEntity.WithdrawnEvent event) {
    log.info("Event: {}", event);

    var command = new WithdrawalReductionTreeEntity.TrunkCreateCommand(event.accountId(), event.withdrawalId(), event.amount());

    return effects()
        .forward(componentClient.forEventSourcedEntity(event.accountId())
            .call(WithdrawalReductionTreeEntity::createTrunk)
            .params(command));
  }
}
