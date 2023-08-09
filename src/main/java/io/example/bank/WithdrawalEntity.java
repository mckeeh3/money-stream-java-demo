package io.example.bank;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.grpc.Status;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("withdrawalId")
@TypeId("withdrawal")
@RequestMapping("/withdrawal/{withdrawalId}")
public class WithdrawalEntity extends EventSourcedEntity<WithdrawalEntity.State, WithdrawalEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(WithdrawalEntity.class);
  private final String entityId;

  public WithdrawalEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/withdraw")
  public Effect<String> withdraw(@RequestBody WithdrawCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    if (!currentState().isEmpty()) {
      return effects().error("Withdrawal already exists", Status.Code.ALREADY_EXISTS);
    }

    return Validator.<Effect<String>>start()
        .isNull(command.withdrawalId(), "Cannot withdraw without withdrawalId")
        .isTrue(command.withdrawalId().isEmpty(), "Cannot withdraw without withdrawalId")
        .isEmpty(command.withdrawalId.accountId(), "Cannot withdraw without accountId")
        .isEmpty(command.withdrawalId.withdrawalId(), "Cannot withdraw without withdrawalId")
        .isPositive(command.amount(), "Withdraw amount must be positive")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/approve")
  public Effect<String> approve(@RequestBody WithdrawalApprovedCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/reject")
  public Effect<String> reject(@RequestBody WithdrawalRejectedCommand command) {
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
  public State on(WithdrawnEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(WithdrawalApprovedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(WithdrawalRejectedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record WithdrawalId(String accountId, String withdrawalId) {
    boolean isEmpty() {
      return accountId == null || accountId.isEmpty()
          || withdrawalId == null || withdrawalId.isEmpty();
    }

    String toEntityId() {
      return String.format("%s_%s", accountId, withdrawalId);
    }
  }

  public record State(
      WithdrawalId withdrawalId,
      boolean approved,
      boolean rejected,
      LocalDateTime lastUpdated,
      BigDecimal amount) {

    static State emptyState() {
      return new State(null, false, false, LocalDateTime.of(0, 1, 1, 0, 0), BigDecimal.ZERO);
    }

    boolean isEmpty() {
      return withdrawalId == null || withdrawalId.isEmpty();
    }

    Event eventFor(WithdrawCommand command) {
      return new WithdrawnEvent(command.withdrawalId(), command.amount());
    }

    Event eventFor(WithdrawalApprovedCommand command) {
      return new WithdrawalApprovedEvent(command.withdrawalId(), amount);
    }

    Event eventFor(WithdrawalRejectedCommand command) {
      return new WithdrawalRejectedEvent(command.withdrawalId());
    }

    State on(WithdrawnEvent event) {
      return new State(event.withdrawalId(), approved, rejected, LocalDateTime.now(), event.amount());
    }

    State on(WithdrawalApprovedEvent event) {
      return new State(withdrawalId, true, false, LocalDateTime.now(), amount);
    }

    State on(WithdrawalRejectedEvent event) {
      return new State(withdrawalId, false, true, LocalDateTime.now(), amount);
    }
  }

  public interface Event {}

  public record WithdrawCommand(WithdrawalId withdrawalId, BigDecimal amount) {}

  public record WithdrawnEvent(WithdrawalId withdrawalId, BigDecimal amount) implements Event {}

  public record WithdrawalApprovedCommand(WithdrawalId withdrawalId) {}

  public record WithdrawalApprovedEvent(WithdrawalId withdrawalId, BigDecimal amount) implements Event {}

  public record WithdrawalRejectedCommand(WithdrawalId withdrawalId) {}

  public record WithdrawalRejectedEvent(WithdrawalId withdrawalId) implements Event {}
}
