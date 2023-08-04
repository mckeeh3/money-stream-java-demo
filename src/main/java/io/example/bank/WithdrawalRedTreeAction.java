package io.example.bank;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        .map(subbranch -> new WithdrawalRedTreeEntity.BranchCreateCommand(event.accountId(), event.withdrawalId(), subbranch.branchId(), event.branchId(), subbranch.amountToWithdraw()))
        .map(command -> componentClient.forEventSourcedEntity(command.branchId())
            .call(WithdrawalRedTreeEntity::createBranch)
            .params(command))
        .map(deferredCall -> deferredCall.execute())
        .toList();

    var resultsLeaves = event.subbranches().stream()
        .filter(subbranch -> subbranch.amountToWithdraw().compareTo(WithdrawalRedTreeEntity.maxLeafAmount) <= 0)
        .map(subbranch -> new WithdrawalRedLeafEntity.LeafCreateCommand(event.accountId(), event.withdrawalId(), subbranch.branchId(), event.branchId(), subbranch.amountToWithdraw()))
        .map(command -> componentClient.forEventSourcedEntity(command.leafId())
            .call(WithdrawalRedLeafEntity::createLeaf)
            .params(command))
        .map(deferredCall -> deferredCall.execute())
        .toList();

    var results = Stream.concat(resultsBranches.stream(), resultsLeaves.stream()).toList();

    return effects().asyncReply(waitForCallsToComplete(results));
  }

  private CompletionStage<String> waitForCallsToComplete(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
        .thenApply(__ -> "OK");
  }
}
