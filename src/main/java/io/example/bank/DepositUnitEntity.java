package io.example.bank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.example.bank.WithdrawalRedLeafEntity.WithdrawalRedLeafId;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("unitId")
@TypeId("depositUnit")
@RequestMapping("/depositUnit/{unitId}")
public class DepositUnitEntity extends EventSourcedEntity<DepositUnitEntity.State, DepositUnitEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(DepositUnitEntity.class);
  private static final BigDecimal maxUnitAmount = BigDecimal.valueOf(25.00);
  private final String entityId;

  public DepositUnitEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PostMapping("/modifyAmount")
  public Effect<String> modifyAmount(@RequestBody ModifyAmountCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    if (currentState().isDuplicateCommand(command)) {
      return effects().reply("OK");
    }

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PostMapping("/withdraw")
  public Effect<String> withdraw(@RequestBody WithdrawCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PostMapping("/cancelWithdrawal")
  public Effect<String> cancelWithdrawal(@RequestBody WithdrawalCancelCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Deposit unit not found")
        .onError(errorMessage -> effects().error(errorMessage))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(ModifiedAmountEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(WithdrawnEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(WithdrawalCancelledEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record DepositUnitId(String accountId, String depositId, String unitId) {
    boolean isEmpty() {
      return accountId == null || accountId.isEmpty()
          || depositId == null || depositId.isEmpty()
          || unitId == null || unitId.isEmpty();
    }

    DepositUnitId childId() {
      return new DepositUnitId(accountId, depositId, UUID.randomUUID().toString());
    }

    String toEntityId() {
      return "%s_%s_%s".formatted(accountId, depositId, unitId);
    }
  }

  public record State(
      DepositUnitId depositUnitId,
      BigDecimal amount,
      BigDecimal balance,
      LocalDateTime lastUpdated,
      List<WithdrawLeaf> withdrawals) {

    static State emptyState() {
      return new State(null, null, null, null, List.of());
    }

    boolean isEmpty() {
      return depositUnitId == null || depositUnitId.isEmpty();
    }

    boolean isDuplicateCommand(ModifyAmountCommand command) {
      return amount != null && command.amount().compareTo(amount) >= 0;
    }

    boolean isAvailableForWithdrawal() {
      return amount != null && amount.compareTo(maxUnitAmount) < 0;
    }

    Event eventFor(ModifyAmountCommand command) {
      if (isAmountAdjustmentCompleted(command.amount)) {
        return new ModifiedAmountEvent(command.depositUnitId, command.amount, List.of());
      }
      var modifyAmounts = distributeAmount(command.amount())
          .stream().map(amount -> new ModifyAmount(command.depositUnitId.childId(), amount)).toList();
      var firstModifyAmount = new ModifyAmount(command.depositUnitId, modifyAmounts.get(0).amount());
      modifyAmounts = Stream.concat(Stream.of(firstModifyAmount), modifyAmounts.stream().skip(1)).toList();

      return new ModifiedAmountEvent(command.depositUnitId, command.amount, modifyAmounts);
    }

    Event eventFor(WithdrawCommand command) {
      var currentBalance = amount.subtract(sum(withdrawals));
      var withdrawalAmount = command.withDrawalRequestAmount().min(currentBalance);
      var newBalance = currentBalance.subtract(withdrawalAmount);
      var depositUnit = new DepositUnit(depositUnitId, amount, newBalance, withdrawalAmount);
      return new WithdrawnEvent(command.withdrawalRedLeafId, depositUnit);
    }

    Event eventFor(WithdrawalCancelCommand command) {
      var filtered = withdrawals.stream().filter(w -> !w.withdrawalRedLeafId().equals(command.withdrawalRedLeafId())).toList();
      var newBalance = amount.subtract(sum(filtered));
      return new WithdrawalCancelledEvent(command.withdrawalRedLeafId(), amount, newBalance);
    }

    State on(ModifiedAmountEvent event) {
      return new State(event.depositUnitId, event.amount(), balance, LocalDateTime.now(), withdrawals);
    }

    State on(WithdrawnEvent event) {
      var withdrawalAmount = event.depositUnit.amountWithdrawn();
      var withdrawal = new WithdrawLeaf(event.withdrawalRedLeafId(), withdrawalAmount);
      var filtered = withdrawals.stream().filter(w -> !w.withdrawalRedLeafId().equals(event.withdrawalRedLeafId())).toList();
      var newWithdrawals = Stream.concat(filtered.stream(), Stream.of(withdrawal)).toList();
      var newBalance = amount.subtract(sum(newWithdrawals));

      return new State(depositUnitId, amount, newBalance, LocalDateTime.now(), newWithdrawals);
    }

    State on(WithdrawalCancelledEvent event) {
      var filtered = withdrawals.stream().filter(w -> !w.withdrawalRedLeafId().equals(event.withdrawalRedLeafId())).toList();
      var newBalance = amount.subtract(sum(filtered));
      return new State(depositUnitId, amount, newBalance, LocalDateTime.now(), filtered);
    }
  }

  static boolean isAmountAdjustmentCompleted(BigDecimal amount) {
    return amount.compareTo(maxUnitAmount) <= 0;
  }

  private static List<BigDecimal> distributeAmount(BigDecimal amount) {
    var cents = amount.multiply(BigDecimal.valueOf(100)).longValue();
    var centsPerChild = cents / maxUnitAmount.longValue();
    var remainder = cents % maxUnitAmount.longValue();

    BigDecimal dollarsPerChild = new BigDecimal(centsPerChild).divide(new BigDecimal(100));

    return IntStream.range(0, maxUnitAmount.intValue())
        .mapToObj(i -> dollarsPerChild.add(i < remainder ? BigDecimal.valueOf(0.01) : BigDecimal.valueOf(0.00)))
        .toList();
  }

  private static BigDecimal sum(List<WithdrawLeaf> withdrawals) {
    return withdrawals.stream().map(withdrawal -> withdrawal.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public interface Event {}

  public record ModifyAmountCommand(DepositUnitId depositUnitId, BigDecimal amount) {}

  public record ModifyAmount(DepositUnitId depositUnitId, BigDecimal amount) {}

  public record ModifiedAmountEvent(DepositUnitId depositUnitId, BigDecimal amount, List<ModifyAmount> modifyAmounts) implements Event {}

  public record DepositUnit(DepositUnitId depositUnitId, BigDecimal amount, BigDecimal balance, BigDecimal amountWithdrawn) {}

  public record WithdrawLeaf(WithdrawalRedLeafId withdrawalRedLeafId, BigDecimal amount) {}

  public record WithdrawCommand(WithdrawalRedLeafId withdrawalRedLeafId, BigDecimal withDrawalRequestAmount) {}

  public record WithdrawnEvent(WithdrawalRedLeafId withdrawalRedLeafId, DepositUnit depositUnit) implements Event {}

  public record WithdrawalCancelCommand(WithdrawalRedLeafId withdrawalRedLeafId) {}

  public record WithdrawalCancelledEvent(WithdrawalRedLeafId withdrawalRedLeafId, BigDecimal amount, BigDecimal balance) implements Event {}
}
