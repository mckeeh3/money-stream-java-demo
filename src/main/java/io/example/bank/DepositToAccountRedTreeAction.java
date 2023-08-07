package io.example.bank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = DepositEntity.class, ignoreUnknown = true)
public class DepositToAccountRedTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(DepositToAccountRedTreeAction.class);
  private final ComponentClient componentClient;

  public DepositToAccountRedTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(DepositEntity.DepositedEvent event) {
    log.info("Event: {}", event);

    var subBranchId = AccountRedTreeEntity.BranchId.forLeaf(event.depositId().accountId(), event.depositId().depositId());
    var branchId = subBranchId.levelUp();
    var command = new AccountRedTreeEntity.UpdateSubbranchCommand(branchId, subBranchId, event.amount());

    return effects()
        .forward(componentClient.forEventSourcedEntity(branchId.toEntityId())
            .call(AccountRedTreeEntity::updateSubbranch)
            .params(command));
  }
}
