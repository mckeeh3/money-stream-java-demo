package io.example.bank;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = DepositEntity.class, ignoreUnknown = true)
public class DepositToDepositUnitAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(DepositToDepositUnitAction.class);
  private final ComponentClient componentClient;

  public DepositToDepositUnitAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(DepositEntity.DepositedEvent event) {
    log.info("Event: {}", event);

    var unitId = UUID.randomUUID().toString();
    var depositUnitId = new DepositUnitEntity.DepositUnitId(event.depositId().accountId(), event.depositId().depositId(), unitId);
    var command = new DepositUnitEntity.ModifyAmountCommand(depositUnitId, event.amount());

    return effects()
        .forward(componentClient.forEventSourcedEntity(depositUnitId.toEntityId())
            .call(DepositUnitEntity::modifyAmount)
            .params(command));
  }
}
