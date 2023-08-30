package io.example.bank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.grpc.Status;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("withdrawalRedTreeId")
@TypeId("withdrawalRedTree")
@RequestMapping("/withdrawalRedTree/{withdrawalRedTreeId}")
public class WithdrawalRedTreeEntity extends EventSourcedEntity<WithdrawalRedTreeEntity.State, WithdrawalRedTreeEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(WithdrawalRedTreeEntity.class);
  static final BigDecimal maxLeafAmount = BigDecimal.valueOf(25.00);
  private final String entityId;

  public WithdrawalRedTreeEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PostMapping("/createTrunk")
  public Effect<String> createTrunk(@RequestBody TrunkCreateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PostMapping("/createBranch")
  public Effect<String> createBranch(@RequestBody BranchCreateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/updateAmountWithdrawn")
  public Effect<String> updateAmountWithdrawn(@RequestBody UpdateAmountWithdrawnCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  // Insufficient funds messaging works from the top of the tree down to the truck.
  @PatchMapping("/insufficientFunds")
  public Effect<String> insufficientFunds(@RequestBody InsufficientFundsCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    if (currentState().insufficientFunds) {
      return effects().reply("OK");
    }

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  // When an insufficient funds message reaches the trunk, the trunk emits a canceled withdrawal event
  // that cascades up to the top of the tree to the leaves.
  @PatchMapping("/cancelWithdrawal")
  public Effect<String> cancelWithdrawal(@RequestBody CancelWithdrawalCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    if (currentState().insufficientFunds) {
      return effects().reply("OK");
    }

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetWithdrawal", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Withdrawal not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(BranchCreatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedAmountWithdrawnEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(WithdrawalApprovedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(InsufficientFundsEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(CanceledWithdrawalEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record WithdrawalRedTreeId(String accountId, String withdrawalId, String branchId) {
    boolean isEmpty() {
      return accountId == null || accountId.isEmpty()
          || withdrawalId == null || withdrawalId.isEmpty()
          || branchId == null || branchId.isEmpty();
    }

    String toEntityId() {
      return "%s_%s_%s".formatted(accountId, withdrawalId, branchId);
    }

    WithdrawalRedTreeId childId() {
      return new WithdrawalRedTreeId(accountId, withdrawalId, UUID.randomUUID().toString());
    }

    static WithdrawalRedTreeId fromEntityId(WithdrawalRedLeafEntity.WithdrawalRedLeafId withdrawalRedLeafId) {
      return new WithdrawalRedTreeId(withdrawalRedLeafId.accountId(), withdrawalRedLeafId.withdrawalId(), withdrawalRedLeafId.leafId());
    }
  }

  public record State(
      WithdrawalRedTreeId withdrawalRedTreeId,
      WithdrawalRedTreeId withdrawalRedTreeParentId,
      LocalDateTime lastUpdated,
      BigDecimal amountToWithdraw,
      BigDecimal amountWithdrawn,
      boolean approved,
      boolean insufficientFunds,
      List<Subbranch> subbranches) {

    static State emptyState() {
      return new State(null, null, LocalDateTime.of(0, 1, 1, 0, 0), BigDecimal.ZERO, BigDecimal.ZERO, false, false, List.of());
    }

    boolean isEmpty() {
      return withdrawalRedTreeId.isEmpty();
    }

    Event eventFor(TrunkCreateCommand command) {
      WithdrawalRedTreeId parentId = null;
      var subbranches = distributeAmount(command.amount()).stream()
          .map(amount -> new Subbranch(command.withdrawalRedTreeId().childId(), amount, BigDecimal.ZERO))
          .toList();
      return new BranchCreatedEvent(command.withdrawalRedTreeId(), parentId, command.amount(), subbranches);
    }

    Event eventFor(BranchCreateCommand command) {
      var subbranches = distributeAmount(command.amount()).stream()
          .map(amount -> new Subbranch(command.withdrawalRedTreeId().childId(), amount, BigDecimal.ZERO))
          .toList();
      return new BranchCreatedEvent(command.withdrawalRedTreeId().childId(), command.withdrawalRedTreeParentId(), command.amount(), subbranches);
    }

    List<Event> eventsFor(UpdateAmountWithdrawnCommand command) {
      var updateEvent = new UpdatedAmountWithdrawnEvent(withdrawalRedTreeId, command.subbranch());
      var newState = on(updateEvent);
      if (isTrunk() && newState.approved) {
        var approvedEvent = new WithdrawalApprovedEvent(command.withdrawalRedTreeId());
        return List.of(updateEvent, approvedEvent);
      }
      return List.of(updateEvent);
    }

    Event eventFor(InsufficientFundsCommand command) {
      if (withdrawalRedTreeParentId == null || withdrawalRedTreeParentId.isEmpty()) {
        return new CanceledWithdrawalEvent(withdrawalRedTreeId, subbranches);
      }
      return new InsufficientFundsEvent(withdrawalRedTreeId, withdrawalRedTreeParentId);
    }

    Event eventFor(CancelWithdrawalCommand command) {
      return new CanceledWithdrawalEvent(withdrawalRedTreeParentId, subbranches);
    }

    State on(BranchCreatedEvent event) {
      return new State(
          event.withdrawalRedTreeId(),
          event.withdrawalRedTreeParentId(),
          LocalDateTime.now(),
          event.amount(),
          amountWithdrawn,
          approved,
          insufficientFunds,
          event.subbranches());
    }

    State on(UpdatedAmountWithdrawnEvent event) {
      var newSubbranches = subbranches.stream()
          .map(subbranch -> subbranch.withdrawalRedTreeId().equals(event.subbranch.withdrawalRedTreeId()) ? event.subbranch() : subbranch)
          .toList();

      var newAmountWithdrawn = newSubbranches.stream()
          .map(Subbranch::amountWithdrawn)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      return new State(
          withdrawalRedTreeId,
          withdrawalRedTreeParentId,
          LocalDateTime.now(),
          amountToWithdraw,
          newAmountWithdrawn,
          newAmountWithdrawn.compareTo(amountToWithdraw) == 0,
          insufficientFunds,
          newSubbranches);
    }

    State on(WithdrawalApprovedEvent event) {
      return this;
    }

    State on(InsufficientFundsEvent event) {
      return new State(
          withdrawalRedTreeId,
          withdrawalRedTreeParentId,
          LocalDateTime.now(),
          amountToWithdraw,
          amountWithdrawn,
          approved,
          true,
          subbranches);
    }

    State on(CanceledWithdrawalEvent event) {
      return new State(
          withdrawalRedTreeId,
          withdrawalRedTreeParentId,
          LocalDateTime.now(),
          amountToWithdraw,
          amountWithdrawn,
          false,
          true,
          subbranches);
    }

    boolean isTrunk() {
      return withdrawalRedTreeParentId == null;
    }
  }

  static boolean isTopBranch(BigDecimal amount) {
    return amount.compareTo(maxLeafAmount) <= 0;
  }

  public static List<BigDecimal> distributeAmount(BigDecimal total) {
    var cents = total.multiply(BigDecimal.valueOf(100)).longValue();
    var centsPerChild = cents / maxLeafAmount.longValue();
    var remainder = cents % maxLeafAmount.longValue();

    BigDecimal dollarsPerChild = new BigDecimal(centsPerChild).divide(new BigDecimal(100));

    return IntStream.range(0, maxLeafAmount.intValue())
        .mapToObj(i -> dollarsPerChild.add(i < remainder ? new BigDecimal(".01") : new BigDecimal("0")))
        .toList();
  }

  public interface Event {}

  public record TrunkCreateCommand(WithdrawalRedTreeId withdrawalRedTreeId, BigDecimal amount) {}

  public record BranchCreateCommand(WithdrawalRedTreeId withdrawalRedTreeId, WithdrawalRedTreeId withdrawalRedTreeParentId, BigDecimal amount) {}

  public record Subbranch(WithdrawalRedTreeId withdrawalRedTreeId, BigDecimal amountToWithdraw, BigDecimal amountWithdrawn) {}

  public record BranchCreatedEvent(WithdrawalRedTreeId withdrawalRedTreeId, WithdrawalRedTreeId withdrawalRedTreeParentId, BigDecimal amount, List<Subbranch> subbranches) implements Event {}

  public record UpdateAmountWithdrawnCommand(WithdrawalRedTreeId withdrawalRedTreeId, Subbranch subbranch) {}

  public record UpdatedAmountWithdrawnEvent(WithdrawalRedTreeId withdrawalRedTreeId, Subbranch subbranch) implements Event {}

  public record WithdrawalApprovedEvent(WithdrawalRedTreeId withdrawalRedTreeId) implements Event {}

  public record InsufficientFundsCommand(WithdrawalRedTreeId withdrawalRedTreeId) {}

  public record InsufficientFundsEvent(WithdrawalRedTreeId withdrawalRedTreeId, WithdrawalRedTreeId withdrawalRedTreeParentId) implements Event {}

  public record CancelWithdrawalCommand(WithdrawalRedTreeId withdrawalRedTreeId) {}

  public record CanceledWithdrawalEvent(WithdrawalRedTreeId withdrawalRedTreeId, List<Subbranch> subbranches) implements Event {}
}
