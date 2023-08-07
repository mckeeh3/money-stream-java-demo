package io.example.bank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.example.bank.DepositUnitEntity.DepositUnitId;
import io.example.bank.WithdrawalRedTreeEntity.WithdrawalRedTreeId;
import io.grpc.Status;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("leafId")
@TypeId("withdrawalRedLeaf")
@RequestMapping("/withdrawalRedLeaf/{withdrawalLeafId}")
public class WithdrawalRedLeafEntity extends EventSourcedEntity<WithdrawalRedLeafEntity.State, WithdrawalRedLeafEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(WithdrawalRedLeafEntity.class);
  private final String entityId;

  public WithdrawalRedLeafEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/createLeaf")
  public Effect<String> createLeaf(@RequestBody LeafCreateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    if (!currentState().isEmpty()) {
      return effects().reply("OK");
    }

    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/depositFound")
  public Effect<String> depositFound(@RequestBody DepositFoundCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/depositNotFound")
  public Effect<String> depositNotFound(@RequestBody DepositNotFoundCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

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
        .isTrue(currentState().isEmpty(), "WithdrawalRedLeaf not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(LeafCreatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(DepositSeekEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(DepositFoundEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(FullyFundedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(DepositNotFoundEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(CanceledWithdrawalEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record WithdrawalRedLeafId(String accountId, String withdrawalId, String leafId) {
    boolean isEmpty() {
      return accountId == null || accountId.isEmpty()
          || withdrawalId == null || withdrawalId.isEmpty()
          || leafId == null || leafId.isEmpty();
    }

    String toEntityId() {
      return "%s_%s_%s".formatted(accountId, withdrawalId, leafId);
    }

    static WithdrawalRedLeafId from(WithdrawalRedTreeEntity.WithdrawalRedTreeId withdrawalRedTreeId) {
      return new WithdrawalRedLeafId(withdrawalRedTreeId.accountId(), withdrawalRedTreeId.withdrawalId(), withdrawalRedTreeId.branchId());
    }
  }

  public record State(
      WithdrawalRedLeafId withdrawalRedLeafId,
      WithdrawalRedTreeId parentBranchId,
      LocalDateTime lastUpdated,
      BigDecimal amountToWithdraw,
      BigDecimal amountWithdrawn,
      List<DepositUnit> depositUnits) {

    static State emptyState() {
      return new State(null, null, LocalDateTime.of(0, 1, 1, 0, 0), BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    boolean isEmpty() {
      return withdrawalRedLeafId == null || withdrawalRedLeafId.isEmpty();
    }

    List<Event> eventsFor(LeafCreateCommand command) {
      if (isEmpty()) {
        return List.of(
            new LeafCreatedEvent(command.withdrawalRedLeafId(), command.parentBranchId(), command.amount()),
            new DepositSeekEvent(command.withdrawalRedLeafId(), command.amount()));
      }
      return List.of();
    }

    List<Event> eventsFor(DepositFoundCommand command) {
      var newState = on(new DepositFoundEvent(command.withdrawalRedLeafId(), command.depositUnit(), BigDecimal.ZERO, List.of()));
      var foundEvent = new DepositFoundEvent(command.withdrawalRedLeafId(), command.depositUnit(), amountToWithdraw, depositUnits);
      if (newState.amountWithdrawn().compareTo(newState.amountToWithdraw()) >= 0) {
        var FullyFundedEvent = new FullyFundedEvent(command.withdrawalRedLeafId(), parentBranchId, newState.amountWithdrawn());
        return List.of(foundEvent, FullyFundedEvent);
      }
      var seekEvent = new DepositSeekEvent(command.withdrawalRedLeafId(), newState.amountToWithdraw().subtract(newState.amountWithdrawn()));
      return List.of(foundEvent, seekEvent);
    }

    Event eventFor(DepositNotFoundCommand command) {
      return new DepositNotFoundEvent(command.withdrawalRedLeafId());
    }

    Event eventFor(CancelWithdrawalCommand command) {
      return new CanceledWithdrawalEvent(command.withdrawalRedLeafId(), depositUnits);
    }

    State on(LeafCreatedEvent event) {
      if (isEmpty()) {
        return new State(
            event.withdrawalRedLeafId(),
            event.parentBranchId(),
            LocalDateTime.now(),
            event.amount(),
            BigDecimal.ZERO,
            List.of());
      }
      return this;
    }

    State on(DepositSeekEvent event) {
      return this;
    }

    State on(DepositFoundEvent event) {
      var filteredDepositUnits = depositUnits.stream()
          .filter(depositUnit -> !depositUnit.depositUnitId().equals(event.depositUnit().depositUnitId()))
          .toList();
      var newDepositUnits = Stream.concat(filteredDepositUnits.stream(), Stream.of(event.depositUnit())).toList();
      var newAmountWithdrawn = newDepositUnits.stream()
          .map(DepositUnit::amount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      return new State(
          withdrawalRedLeafId,
          parentBranchId,
          LocalDateTime.now(),
          amountToWithdraw,
          newAmountWithdrawn,
          newDepositUnits);
    }

    State on(FullyFundedEvent event) {
      return this;
    }

    State on(DepositNotFoundEvent event) {
      return this;
    }

    State on(CanceledWithdrawalEvent event) {
      return new State(
          withdrawalRedLeafId,
          parentBranchId,
          LocalDateTime.now(),
          amountToWithdraw,
          BigDecimal.ZERO,
          List.of());
    }
  }

  public interface Event {}

  public record LeafCreateCommand(WithdrawalRedLeafId withdrawalRedLeafId, WithdrawalRedTreeId parentBranchId, BigDecimal amount) {}

  public record LeafCreatedEvent(WithdrawalRedLeafId withdrawalRedLeafId, WithdrawalRedTreeId parentBranchId, BigDecimal amount) implements Event {}

  public record DepositSeekEvent(WithdrawalRedLeafId withdrawalRedLeafId, BigDecimal amountNeeded) implements Event {}

  public record DepositUnit(DepositUnitId depositUnitId, BigDecimal amount) {}

  public record DepositFoundCommand(WithdrawalRedLeafId withdrawalRedLeafId, DepositUnit depositUnit) {}

  public record DepositFoundEvent(WithdrawalRedLeafId withdrawalRedLeafId, DepositUnit depositUnit, BigDecimal amountToWithdraw, List<DepositUnit> depositUnits) implements Event {}

  public record FullyFundedEvent(WithdrawalRedLeafId withdrawalRedLeafId, WithdrawalRedTreeId parentBranchId, BigDecimal amount) implements Event {}

  public record DepositNotFoundCommand(WithdrawalRedLeafId withdrawalRedLeafId) {}

  public record DepositNotFoundEvent(WithdrawalRedLeafId withdrawalRedLeafId) implements Event {}

  public record CancelWithdrawalCommand(WithdrawalRedLeafId withdrawalRedLeafId) {}

  public record CanceledWithdrawalEvent(WithdrawalRedLeafId withdrawalRedLeafId, List<DepositUnit> depositUnits) implements Event {}
}
