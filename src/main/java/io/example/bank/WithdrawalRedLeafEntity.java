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
import io.grpc.Status;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("leafId")
@TypeId("withdrawalRedLeaf")
@RequestMapping("/withdrawalRedLeaf/{leafId}")
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
        .emitEvent(currentState().eventFor(command))
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

  public record State(
      String accountId,
      String withdrawalId,
      String leafId,
      LocalDateTime lastUpdated,
      BigDecimal amountToWithdraw,
      BigDecimal amountWithdrawn,
      List<DepositUnit> depositUnits) {

    static State emptyState() {
      return new State(null, null, null, LocalDateTime.of(0, 1, 1, 0, 0), BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    boolean isEmpty() {
      return withdrawalId == null || withdrawalId.isEmpty();
    }

    Event eventFor(LeafCreateCommand command) {
      return new DepositSeekEvent(command.accountId(), command.withdrawalId(), command.leafId(), command.amount());
    }

    List<Event> eventsFor(DepositFoundCommand command) {
      var newState = on(new DepositFoundEvent(command.accountId(), command.withdrawalId(), command.leafId(), command.depositUnit(), BigDecimal.ZERO, List.of()));
      var foundEvent = new DepositFoundEvent(command.accountId(), command.withdrawalId(), command.leafId(), command.depositUnit(), amountToWithdraw, depositUnits);
      if (newState.amountWithdrawn().compareTo(newState.amountToWithdraw()) >= 0) {
        var FullyFundedEvent = new FullyFundedEvent(command.accountId(), command.withdrawalId(), command.leafId(), newState.amountWithdrawn());
        return List.of(foundEvent, FullyFundedEvent);
      }
      var seekEvent = new DepositSeekEvent(command.accountId(), command.withdrawalId(), command.leafId(), newState.amountToWithdraw().subtract(newState.amountWithdrawn()));
      return List.of(foundEvent, seekEvent);
    }

    Event eventFor(DepositNotFoundCommand command) {
      return new DepositNotFoundEvent(command.accountId(), command.withdrawalId(), command.leafId());
    }

    Event eventFor(CancelWithdrawalCommand command) {
      return new CanceledWithdrawalEvent(command.accountId(), command.withdrawalId(), command.leafId(), depositUnits);
    }

    State on(DepositSeekEvent event) {
      if (isEmpty()) {
        return new State(event.accountId(), event.withdrawalId(), event.leafId(), LocalDateTime.now(), event.amountNeeded(), BigDecimal.ZERO, List.of());
      }
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

      return new State(accountId, withdrawalId, leafId, LocalDateTime.now(), amountToWithdraw, newAmountWithdrawn, newDepositUnits);
    }

    State on(FullyFundedEvent event) {
      return this;
    }

    State on(DepositNotFoundEvent event) {
      return this;
    }

    State on(CanceledWithdrawalEvent event) {
      return new State(accountId, withdrawalId, leafId, LocalDateTime.now(), amountToWithdraw, BigDecimal.ZERO, List.of());
    }
  }

  public interface Event {}

  public record LeafCreateCommand(String accountId, String withdrawalId, String leafId, BigDecimal amount) {}

  public record DepositSeekEvent(String accountId, String withdrawalId, String leafId, BigDecimal amountNeeded) implements Event {}

  public record DepositUnit(String depositAccountId, String depositId, String depositUnitId, BigDecimal amount) {}

  public record DepositFoundCommand(String accountId, String withdrawalId, String leafId, DepositUnit depositUnit) {}

  public record DepositFoundEvent(String accountId, String withdrawalId, String leafId, DepositUnit depositUnit, BigDecimal amountToWithdraw, List<DepositUnit> depositUnits) implements Event {}

  public record FullyFundedEvent(String accountId, String withdrawalId, String leafId, BigDecimal amount) implements Event {}

  public record DepositNotFoundCommand(String accountId, String withdrawalId, String leafId) {}

  public record DepositNotFoundEvent(String accountId, String withdrawalId, String leafId) implements Event {}

  public record CancelWithdrawalCommand(String accountId, String withdrawalId, String leafId) {}

  public record CanceledWithdrawalEvent(String accountId, String withdrawalId, String leafId, List<DepositUnit> depositUnits) implements Event {}
}
