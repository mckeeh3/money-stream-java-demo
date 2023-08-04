package io.example.bank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = DepositUnitEntity.class, ignoreUnknown = true)
public class DepositUnitToWithdrawalRedLeafAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(DepositUnitToWithdrawalRedLeafAction.class);
  private final ComponentClient componentClient;

  public DepositUnitToWithdrawalRedLeafAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(DepositUnitEntity.WithdrawnEvent event) {
    log.info("Event: {}", event);

    var depositUnit = new WithdrawalRedLeafEntity.DepositUnit(event.depositUnit().accountId(), event.depositUnit().depositId(),
        event.depositUnit().unitId(), event.depositUnit().amountWithdrawn());
    var command = new WithdrawalRedLeafEntity.DepositFoundCommand(event.withdrawLeaf().accountId(), event.withdrawLeaf().withdrawalId(),
        event.withdrawLeaf().leafId(), depositUnit);

    return effects()
        .forward(componentClient.forEventSourcedEntity(event.withdrawLeaf().leafId())
            .call(WithdrawalRedLeafEntity::depositFound)
            .params(command));
  }
}
