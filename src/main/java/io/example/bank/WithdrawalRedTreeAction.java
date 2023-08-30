package io.example.bank;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.bank.WithdrawalRedLeafEntity.LeafCreateCommand;
import io.example.bank.WithdrawalRedLeafEntity.WithdrawalRedLeafId;
import io.example.bank.WithdrawalRedTreeEntity.BranchCreateCommand;
import io.example.bank.WithdrawalRedTreeEntity.CancelWithdrawalCommand;
import io.example.bank.WithdrawalRedTreeEntity.Subbranch;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = WithdrawalRedTreeEntity.class, ignoreUnknown = true)
public class WithdrawalRedTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(WithdrawalRedTreeAction.class);
  private final ComponentClient componentClient;

  public WithdrawalRedTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(WithdrawalRedTreeEntity.BranchCreatedEvent event) {
    log.info("Event: {}", event);

    if (event.subbranches().isEmpty()) {
      return effects().reply("OK");
    }

    var resultsBranches = event.subbranches().stream()
        .filter(subbranch -> isBranchAmount(subbranch))
        .map(subbranch -> toCommandBranch(event, subbranch))
        .map(command -> toCallBranch(command))
        .toList();

    var resultsLeaves = event.subbranches().stream()
        .filter(subbranch -> isLeafAmount(subbranch))
        .map(subbranch -> toCommandLeaf(event, subbranch))
        .map(command -> toCallLeaf(command))
        .toList();

    var results = Stream.concat(resultsBranches.stream(), resultsLeaves.stream()).toList();

    return effects().asyncReply(waitForCallsToComplete(results));
  }

  public Effect<String> on(WithdrawalRedTreeEntity.InsufficientFundsEvent event) {
    log.info("Event: {}", event);
    var command = new WithdrawalRedTreeEntity.InsufficientFundsCommand(event.withdrawalRedTreeParentId());

    return effects()
        .forward(componentClient.forEventSourcedEntity(command.withdrawalRedTreeId().toEntityId())
            .call(WithdrawalRedTreeEntity::insufficientFunds)
            .params(command));
  }

  public Effect<String> on(WithdrawalRedTreeEntity.CanceledWithdrawalEvent event) {
    log.info("Event: {}", event);

    if (event.subbranches().isEmpty()) {
      return effects().reply("OK");
    }

    var resultsBranches = event.subbranches().stream()
        .filter(subbranch -> isBranchAmount(subbranch))
        .map(subbranch -> toCommandBranch(subbranch))
        .map(command -> toCallBranch(command))
        .toList();

    var resultsLeaves = event.subbranches().stream()
        .filter(subbranch -> isLeafAmount(subbranch))
        .map(subbranch -> toCommandLeaf(subbranch))
        .map(command -> toCallLeaf(command))
        .toList();

    var results = Stream.concat(resultsBranches.stream(), resultsLeaves.stream()).toList();

    return effects().asyncReply(waitForCallsToComplete(results));
  }

  private static boolean isBranchAmount(Subbranch subbranch) {
    return subbranch.amountToWithdraw().compareTo(WithdrawalRedTreeEntity.maxLeafAmount) > 0;
  }

  private static boolean isLeafAmount(Subbranch subbranch) {
    return subbranch.amountToWithdraw().compareTo(WithdrawalRedTreeEntity.maxLeafAmount) <= 0;
  }

  private static BranchCreateCommand toCommandBranch(WithdrawalRedTreeEntity.BranchCreatedEvent event, Subbranch subbranch) {
    return new WithdrawalRedTreeEntity.BranchCreateCommand(subbranch.withdrawalRedTreeId(), event.withdrawalRedTreeId(), subbranch.amountToWithdraw());
  }

  private CompletionStage<String> toCallBranch(BranchCreateCommand command) {
    return componentClient.forEventSourcedEntity(command.withdrawalRedTreeId().toEntityId())
        .call(WithdrawalRedTreeEntity::createBranch)
        .params(command)
        .execute();
  }

  private static LeafCreateCommand toCommandLeaf(WithdrawalRedTreeEntity.BranchCreatedEvent event, Subbranch subbranch) {
    var withdrawalRedLeafId = WithdrawalRedLeafId.from(subbranch.withdrawalRedTreeId());
    return new WithdrawalRedLeafEntity.LeafCreateCommand(withdrawalRedLeafId, event.withdrawalRedTreeId(), subbranch.amountToWithdraw());
  }

  private CompletionStage<String> toCallLeaf(LeafCreateCommand command) {
    return componentClient.forEventSourcedEntity(command.withdrawalRedLeafId().toEntityId())
        .call(WithdrawalRedLeafEntity::create)
        .params(command)
        .execute();
  }

  private static CancelWithdrawalCommand toCommandBranch(WithdrawalRedTreeEntity.Subbranch subbranch) {
    return new WithdrawalRedTreeEntity.CancelWithdrawalCommand(subbranch.withdrawalRedTreeId());
  }

  private CompletionStage<String> toCallBranch(CancelWithdrawalCommand command) {
    return componentClient.forEventSourcedEntity(command.withdrawalRedTreeId().toEntityId())
        .call(WithdrawalRedTreeEntity::cancelWithdrawal)
        .params(command)
        .execute();
  }

  private static WithdrawalRedLeafEntity.CancelWithdrawalCommand toCommandLeaf(WithdrawalRedTreeEntity.Subbranch subbranch) {
    var withdrawalRedLeafId = WithdrawalRedLeafId.from(subbranch.withdrawalRedTreeId());
    return new WithdrawalRedLeafEntity.CancelWithdrawalCommand(withdrawalRedLeafId);
  }

  private CompletionStage<String> toCallLeaf(WithdrawalRedLeafEntity.CancelWithdrawalCommand command) {
    return componentClient.forEventSourcedEntity(command.withdrawalRedLeafId().toEntityId())
        .call(WithdrawalRedLeafEntity::cancelWithdrawal)
        .params(command)
        .execute();
  }

  private static CompletionStage<String> waitForCallsToComplete(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
        .thenApply(__ -> "OK");
  }
}
