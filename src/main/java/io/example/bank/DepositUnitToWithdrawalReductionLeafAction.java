package io.example.bank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = DepositUnitEntity.class, ignoreUnknown = true)
public class DepositUnitToWithdrawalReductionLeafAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(DepositUnitToWithdrawalReductionLeafAction.class);
  private final ComponentClient componentClient;

  public DepositUnitToWithdrawalReductionLeafAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(DepositUnitEntity.WithdrawnEvent event) {
    log.info("Event: {}", event);

    var depositUnit = new WithdrawalReductionLeafEntity.DepositUnit(event.depositUnit().accountId(), event.depositUnit().depositId(),
        event.depositUnit().unitId(), event.depositUnit().amountWithdrawn());
    var command = new WithdrawalReductionLeafEntity.DepositFoundCommand(event.withdrawLeaf().accountId(), event.withdrawLeaf().withdrawalId(),
        event.withdrawLeaf().leafId(), depositUnit);

    return effects()
        .forward(componentClient.forEventSourcedEntity(event.withdrawLeaf().leafId())
            .call(WithdrawalReductionLeafEntity::depositFound)
            .params(command));
  }
}
