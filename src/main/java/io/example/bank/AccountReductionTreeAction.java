package io.example.bank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = AccountReductionTreeEntity.class, ignoreUnknown = true)
public class AccountReductionTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(AccountReductionTreeAction.class);
  private final ComponentClient componentClient;

  public AccountReductionTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(AccountReductionTreeEntity.UpdatedBranchEvent event) {
    log.info("Event: {}", event);

    var branchId = event.branchId();

    var command = new AccountReductionTreeEntity.ReleaseBranchCommand(event.branchId());
    return effects()
        .forward(componentClient.forEventSourcedEntity(branchId.toEntityId())
            .call(AccountReductionTreeEntity::releaseBranch)
            .params(command));
  }

  public Effect<String> on(AccountReductionTreeEntity.ReleasedBranchEvent event) {
    log.info("Event: {}", event);

    var branchId = event.branchId();

    if (branchId.level() == 0) {
      var command = new AccountEntity.UpdateAccountBalanceCommand(branchId.accountId(), event.subbranch().amount());
      return effects()
          .forward(componentClient.forEventSourcedEntity(branchId.accountId())
              .call(AccountEntity::updateBalance)
              .params(command));
    }

    var upperBranchId = branchId.levelUp();
    var command = new AccountReductionTreeEntity.UpdateSubbranchCommand(upperBranchId, event.subbranch().subbranchId(), event.subbranch().amount());
    return effects()
        .forward(componentClient.forEventSourcedEntity(upperBranchId.toEntityId())
            .call(AccountReductionTreeEntity::updateSubbranch)
            .params(command));
  }
}
