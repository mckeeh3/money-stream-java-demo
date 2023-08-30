package io.example.bank;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
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

@Id("depositId")
@TypeId("deposit")
@RequestMapping("/deposit/{depositId}")
public class DepositEntity extends EventSourcedEntity<DepositEntity.State, DepositEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(DepositEntity.class);
  private final String entityId;

  public DepositEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody DepositCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return Validator.<Effect<String>>start()
        .isEmpty(command.depositId().accountId, "Cannot deposit without accountId")
        .isEmpty(command.depositId().depositId, "Cannot deposit without depositId")
        .isPositive(command.amount(), "Deposit amount must be positive")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetDeposit", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Deposit not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(DepositedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record DepositId(String accountId, String depositId) {
    boolean isEmpty() {
      return accountId == null || accountId.isEmpty() ||
          depositId == null || depositId.isEmpty();
    }

    String toEntityId() {
      return "%s_%s".formatted(accountId, depositId);
    }
  }

  public record State(
      DepositId depositId,
      LocalDateTime lastUpdated,
      BigDecimal amount) {

    static State emptyState() {
      return new State(null, LocalDateTime.of(0, 1, 1, 0, 0), BigDecimal.ZERO);
    }

    boolean isEmpty() {
      return depositId == null || depositId.isEmpty();
    }

    Event eventFor(DepositCommand command) {
      return new DepositedEvent(command.depositId(), command.amount());
    }

    State on(DepositedEvent event) {
      return new State(event.depositId(), LocalDateTime.now(), event.amount());
    }
  }

  public interface Event {}

  public record DepositCommand(DepositId depositId, BigDecimal amount) {}

  public record DepositedEvent(DepositId depositId, BigDecimal amount) implements Event {}
}
