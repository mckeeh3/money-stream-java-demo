package io.example.bank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.grpc.Status;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("branchId")
@TypeId("withdrawalRedTree")
@RequestMapping("/withdrawalRedTree/{branchId}")
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

  @PutMapping("/createTrunk")
  public Effect<String> createTrunk(@RequestBody TrunkCreateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/createBranch")
  public Effect<String> createBranch(@RequestBody BranchCreateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/updateAmountWithdrawn")
  public Effect<String> updateAmountWithdrawn(@RequestBody UpdateAmountWithdrawnCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  // Insufficient funds messaging works from the top of the tree down to the truck.
  @PutMapping("/insufficientFunds")
  public Effect<String> insufficientFunds(@RequestBody InsufficientFundsCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  // When an insufficient funds message reaches the trunk, the trunk emits a canceled withdrawal event
  // that cascades up to the top of the tree to the leaves.
  @PutMapping("/cancelWithdrawal")
  public Effect<String> cancelWithdrawal(@RequestBody CancelWithdrawalCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

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
  public State on(InsufficientFundsEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(CanceledWithdrawalEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String accountId,
      String withdrawalId,
      String branchId,
      String parentBranchId,
      LocalDateTime lastUpdated,
      BigDecimal amountToWithdraw,
      BigDecimal amountWithdrawn,
      boolean isApproved,
      boolean isCanceled,
      List<Subbranch> subbranches) {

    static State emptyState() {
      return new State(null, null, null, null, LocalDateTime.of(0, 1, 1, 0, 0), BigDecimal.ZERO, BigDecimal.ZERO, false, false, List.of());
    }

    boolean isEmpty() {
      return withdrawalId == null || withdrawalId.isEmpty();
    }

    Event eventFor(TrunkCreateCommand command) {
      String parentId = null;
      var subbranches = distributeAmount(command.amount()).stream()
          .map(amount -> new Subbranch(command.accountId(), command.withdrawalId(), UUID.randomUUID().toString(), amount, BigDecimal.ZERO))
          .toList();
      return new BranchCreatedEvent(command.accountId(), command.withdrawalId(), command.branchId(), parentId, command.amount(), subbranches);
    }

    Event eventFor(BranchCreateCommand command) {
      var branchId = UUID.randomUUID().toString();
      var subbranches = distributeAmount(command.amount()).stream()
          .map(amount -> new Subbranch(command.accountId(), command.withdrawalId(), UUID.randomUUID().toString(), amount, BigDecimal.ZERO))
          .toList();
      return new BranchCreatedEvent(command.accountId(), command.withdrawalId(), branchId, command.parentBranchId(), command.amount(), subbranches);
    }

    Event eventFor(UpdateAmountWithdrawnCommand command) {
      return new UpdatedAmountWithdrawnEvent(command.accountId(), command.withdrawalId(), parentBranchId, command.branchId(), command.subbranch());
    }

    Event eventFor(InsufficientFundsCommand command) {
      if (parentBranchId == null) {
        return new CanceledWithdrawalEvent(command.accountId(), command.withdrawalId(), parentBranchId, subbranches.stream().map(Subbranch::branchId).toList());
      }
      return new InsufficientFundsEvent(command.accountId(), command.withdrawalId(), command.branchId(), parentBranchId);
    }

    Event eventFor(CancelWithdrawalCommand command) {
      return new CanceledWithdrawalEvent(command.accountId(), command.withdrawalId(), parentBranchId, subbranches.stream().map(Subbranch::branchId).toList());
    }

    State on(BranchCreatedEvent event) {
      return new State(
          event.accountId(),
          event.withdrawalId(),
          event.branchId(),
          event.parentBranchId(),
          LocalDateTime.now(),
          event.amount(),
          amountWithdrawn,
          isApproved,
          isCanceled,
          event.subbranches());
    }

    State on(UpdatedAmountWithdrawnEvent event) {
      var newSubbranches = subbranches.stream()
          .map(subbranch -> subbranch.branchId().equals(event.subbranch.branchId()) ? event.subbranch() : subbranch)
          .toList();

      var newAmountWithdrawn = newSubbranches.stream()
          .map(Subbranch::amountWithdrawn)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      return new State(
          accountId,
          withdrawalId,
          branchId,
          parentBranchId,
          LocalDateTime.now(),
          amountToWithdraw,
          newAmountWithdrawn,
          newAmountWithdrawn.compareTo(amountToWithdraw) == 0,
          isCanceled,
          newSubbranches);
    }

    State on(InsufficientFundsEvent event) {
      return new State(
          accountId,
          withdrawalId,
          branchId,
          parentBranchId,
          LocalDateTime.now(),
          amountToWithdraw,
          amountWithdrawn,
          isApproved,
          true,
          subbranches);
    }

    State on(CanceledWithdrawalEvent event) {
      return new State(
          accountId,
          withdrawalId,
          branchId,
          parentBranchId,
          LocalDateTime.now(),
          amountToWithdraw,
          amountWithdrawn,
          isApproved,
          true,
          subbranches);
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

  public record TrunkCreateCommand(String accountId, String withdrawalId, String branchId, BigDecimal amount) {}

  public record BranchCreateCommand(String accountId, String withdrawalId, String branchId, String parentBranchId, BigDecimal amount) {}

  public record Subbranch(String accountId, String transactionId, String branchId, BigDecimal amountToWithdraw, BigDecimal amountWithdrawn) {}

  public record BranchCreatedEvent(String accountId, String withdrawalId, String branchId, String parentBranchId, BigDecimal amount, List<Subbranch> subbranches) implements Event {}

  public record UpdateAmountWithdrawnCommand(String accountId, String withdrawalId, String branchId, Subbranch subbranch) {}

  public record UpdatedAmountWithdrawnEvent(String accountId, String withdrawalId, String parentBranchId, String branchId, Subbranch subbranch) implements Event {}

  public record InsufficientFundsCommand(String accountId, String withdrawalId, String branchId) {}

  public record InsufficientFundsEvent(String accountId, String withdrawalId, String branchId, String parentBranchId) implements Event {}

  public record CancelWithdrawalCommand(String accountId, String withdrawalId, String branchId) {}

  public record CanceledWithdrawalEvent(String accountId, String withdrawalId, String parentBranchId, List<String> subbranchIds) implements Event {}
}
