package io.example.bank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = AccountRedTreeEntity.class, ignoreUnknown = true)
public class AccountRedTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(AccountRedTreeAction.class);
  private final ComponentClient componentClient;

  public AccountRedTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(AccountRedTreeEntity.UpdatedBranchEvent event) {
    log.info("Event: {}", event);

    var branchId = event.branchId();

    var command = new AccountRedTreeEntity.ReleaseBranchCommand(event.branchId());
    return effects()
        .forward(componentClient.forEventSourcedEntity(branchId.toEntityId())
            .call(AccountRedTreeEntity::releaseBranch)
            .params(command));
  }

  public Effect<String> on(AccountRedTreeEntity.ReleasedBranchEvent event) {
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
    var command = new AccountRedTreeEntity.UpdateSubbranchCommand(upperBranchId, event.subbranch().subbranchId(), event.subbranch().amount());
    return effects()
        .forward(componentClient.forEventSourcedEntity(upperBranchId.toEntityId())
            .call(AccountRedTreeEntity::updateSubbranch)
            .params(command));
  }
}
