package io.example.bank;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = WithdrawalEntity.class, ignoreUnknown = true)
public class WithdrawalToAccountRedTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(WithdrawalToAccountRedTreeAction.class);
  private final ComponentClient componentClient;

  public WithdrawalToAccountRedTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(WithdrawalEntity.WithdrawalApprovedEvent event) {
    log.info("Event {}", event);

    var subbranchId = AccountRedTreeEntity.BranchId.forLeaf(event.withdrawalId().accountId(), event.withdrawalId().toEntityId());
    var branchId = subbranchId.levelUp();
    var withdrawalAmount = BigDecimal.ZERO.subtract(event.amount());
    var command = new AccountRedTreeEntity.UpdateSubbranchCommand(branchId, subbranchId, withdrawalAmount);

    return effects()
        .forward(componentClient.forEventSourcedEntity(branchId.toEntityId())
            .call(AccountRedTreeEntity::updateSubbranch)
            .params(command));
  }
}
