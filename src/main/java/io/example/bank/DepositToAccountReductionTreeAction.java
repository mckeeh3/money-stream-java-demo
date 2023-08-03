package io.example.bank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = DepositEntity.class, ignoreUnknown = true)
public class DepositToAccountReductionTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(DepositToAccountReductionTreeAction.class);
  private final ComponentClient componentClient;

  public DepositToAccountReductionTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(DepositEntity.DepositedEvent event) {
    log.info("Event: {}", event);

    var subBranchId = AccountReductionTreeEntity.BranchId.forLeaf(event.accountId(), event.depositId());
    var branchId = subBranchId.levelUp();
    var command = new AccountReductionTreeEntity.UpdateSubbranchCommand(branchId, subBranchId, event.amount());

    return effects()
        .forward(componentClient.forEventSourcedEntity(branchId.toEntityId())
            .call(AccountReductionTreeEntity::updateSubbranch)
            .params(command));
  }
}
