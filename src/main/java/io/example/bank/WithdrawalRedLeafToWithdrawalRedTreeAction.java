package io.example.bank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = WithdrawalRedLeafEntity.class, ignoreUnknown = true)
public class WithdrawalRedLeafToWithdrawalRedTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(WithdrawalRedLeafToWithdrawalRedTreeAction.class);
  private final ComponentClient componentClient;

  public WithdrawalRedLeafToWithdrawalRedTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(WithdrawalRedLeafEntity.FullyFundedEvent event) {
    log.info("Event: {}", event);
    var withdrawalRedLeafId = event.withdrawalRedLeafId();
    var subbranchId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId(withdrawalRedLeafId.accountId(), withdrawalRedLeafId.withdrawalId(), withdrawalRedLeafId.leafId());
    var subbranch = new WithdrawalRedTreeEntity.Subbranch(subbranchId, event.amount(), event.amount());
    var command = new WithdrawalRedTreeEntity.UpdateAmountWithdrawnCommand(event.parentBranchId(), subbranch);

    return effects()
        .forward(componentClient.forEventSourcedEntity(command.withdrawalRedTreeId().toEntityId())
            .call(WithdrawalRedTreeEntity::updateAmountWithdrawn)
            .params(command));
  }
}
