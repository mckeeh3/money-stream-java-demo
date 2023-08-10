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
        .filter(subbranch -> subbranch.amountToWithdraw().compareTo(WithdrawalRedTreeEntity.maxLeafAmount) > 0)
        .map(subbranch -> toCommandBranch(event, subbranch))
        .map(command -> toCallBranch(command))
        .toList();

    var resultsLeaves = event.subbranches().stream()
        .filter(subbranch -> subbranch.amountToWithdraw().compareTo(WithdrawalRedTreeEntity.maxLeafAmount) <= 0)
        .map(subbranch -> toCommandLeaf(event, subbranch))
        .map(command -> toCallLeaf(command))
        .toList();

    var results = Stream.concat(resultsBranches.stream(), resultsLeaves.stream()).toList();

    return effects().asyncReply(waitForCallsToComplete(results));
  }

  public Effect<String> on(WithdrawalRedLeafEntity.InsufficientFundsEvent event) {
    log.info("Event: {}", event);

    return effects().reply("OK");
  }

  private BranchCreateCommand toCommandBranch(WithdrawalRedTreeEntity.BranchCreatedEvent event, Subbranch subbranch) {
    return new WithdrawalRedTreeEntity.BranchCreateCommand(subbranch.withdrawalRedTreeId(), event.withdrawalRedTreeId(), subbranch.amountToWithdraw());
  }

  private CompletionStage<String> toCallBranch(BranchCreateCommand command) {
    return componentClient.forEventSourcedEntity(command.withdrawalRedTreeId().toEntityId())
        .call(WithdrawalRedTreeEntity::createBranch)
        .params(command)
        .execute();
  }

  private LeafCreateCommand toCommandLeaf(WithdrawalRedTreeEntity.BranchCreatedEvent event, Subbranch subbranch) {
    return new WithdrawalRedLeafEntity.LeafCreateCommand(WithdrawalRedLeafId.from(subbranch.withdrawalRedTreeId()), event.withdrawalRedTreeId(), subbranch.amountToWithdraw());
  }

  private CompletionStage<String> toCallLeaf(LeafCreateCommand command) {
    return componentClient.forEventSourcedEntity(command.withdrawalRedLeafId().toEntityId())
        .call(WithdrawalRedLeafEntity::createLeaf)
        .params(command)
        .execute();
  }

  private CompletionStage<String> waitForCallsToComplete(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
        .thenApply(__ -> "OK");
  }
}
