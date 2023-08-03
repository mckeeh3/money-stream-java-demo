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
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("accountId")
@TypeId("account")
@RequestMapping("/account/{accountId}")
public class AccountEntity extends EventSourcedEntity<AccountEntity.State, AccountEntity.Event> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public AccountEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/create")
  public Effect<String> create(@RequestBody CreateAccountCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    if (currentState().accountId() != null) {
      return effects().reply("OK");
    }

    return Validator.<Effect<String>>start()
        .isEmpty(command.accountId(), "Cannot create Account without accountId")
        .isEmpty(command.fullName(), "Cannot create Account without fullName")
        .isEmpty(command.address(), "Cannot create Account without address")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/updateBalance")
  public Effect<String> updateBalance(@RequestBody UpdateAccountBalanceCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetAccount", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Account not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedAccountEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedAccountBalanceEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String accountId,
      String fullName,
      String address,
      LocalDateTime lastUpdated,
      BigDecimal balance) {

    static State emptyState() {
      return new State(null, null, null, LocalDateTime.of(0, 1, 1, 0, 0), new BigDecimal(0));
    }

    boolean isEmpty() {
      return accountId == null || accountId.isEmpty();
    }

    Event eventFor(CreateAccountCommand command) {
      return new CreatedAccountEvent(command.accountId(), command.fullName(), command.address());
    }

    Event eventFor(UpdateAccountBalanceCommand command) {
      return new UpdatedAccountBalanceEvent(command.accountId(), command.balance());
    }

    State on(CreatedAccountEvent event) {
      return new State(event.accountId(), event.fullName(), event.address(), LocalDateTime.now(), BigDecimal.valueOf(0));
    }

    State on(UpdatedAccountBalanceEvent event) {
      return new State(accountId, fullName, address, LocalDateTime.now(), event.balance());
    }
  }

  public interface Event {}

  public record CreateAccountCommand(String accountId, String fullName, String address) {}

  public record CreatedAccountEvent(String accountId, String fullName, String address) implements Event {}

  public record UpdateAccountBalanceCommand(String accountId, BigDecimal balance) {}

  public record UpdatedAccountBalanceEvent(String accountId, BigDecimal balance) implements Event {}
}
