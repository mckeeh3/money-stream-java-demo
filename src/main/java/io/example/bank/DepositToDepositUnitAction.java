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
    var command = new DepositUnitEntity.ModifyAmountCommand(event.accountId(), event.depositId(), unitId, event.amount());

    return effects()
        .forward(componentClient.forEventSourcedEntity(unitId)
            .call(DepositUnitEntity::modifyAmount)
            .params(command));
  }
}
