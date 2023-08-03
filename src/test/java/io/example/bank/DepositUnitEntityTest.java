package io.example.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

public class DepositUnitEntityTest {
  @Test
  public void setAmountHigh() {
    var testKit = EventSourcedTestKit.of(DepositUnitEntity::new);

    var startTime = LocalDateTime.now();

    {
      var command = new DepositUnitEntity.ModifyAmountCommand("accountId", "depositId", "unitId", BigDecimal.valueOf(543.21));
      var result = testKit.call(e -> e.modifyAmount(command));
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositUnitEntity.ModifiedAmountEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("depositId", event.depositId());
      assertEquals("unitId", event.unitId());
      assertEquals(0, event.amount().compareTo(BigDecimal.valueOf(543.21)));

      var modifyAmounts = event.modifyAmounts();
      assertTrue(modifyAmounts.size() > 1);
      assertEquals("unitId", modifyAmounts.get(0).unitId());
      assertEquals(-1, modifyAmounts.get(0).amount().compareTo(BigDecimal.valueOf(543.21)));

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("depositId", state.depositId());
      assertEquals("unitId", state.unitId());
      assertEquals(0, state.amount().compareTo(BigDecimal.valueOf(543.21)));
      assertTrue(startTime.isBefore(state.lastUpdated()));
    }
  }

  @Test
  public void setAmountLow() {
    var testKit = EventSourcedTestKit.of(DepositUnitEntity::new);

    var startTime = LocalDateTime.now();

    {
      var command = new DepositUnitEntity.ModifyAmountCommand("accountId", "depositId", "unitId", BigDecimal.valueOf(12.34));
      var result = testKit.call(e -> e.modifyAmount(command));
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositUnitEntity.ModifiedAmountEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("depositId", event.depositId());
      assertEquals("unitId", event.unitId());
      assertEquals(0, event.amount().compareTo(BigDecimal.valueOf(12.34)));

      var modifyAmounts = event.modifyAmounts();
      assertEquals(0, modifyAmounts.size());

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("depositId", state.depositId());
      assertEquals("unitId", state.unitId());
      assertEquals(0, state.amount().compareTo(BigDecimal.valueOf(12.34)));
      assertTrue(startTime.isBefore(state.lastUpdated()));
    }
  }

  @Test
  public void withdrawalTest() {
    var testKit = EventSourcedTestKit.of(DepositUnitEntity::new);

    var startTime = LocalDateTime.now();
    var amount = BigDecimal.valueOf(12.34);
    var withdrawalAmount1 = BigDecimal.valueOf(2.34);
    var withdrawalAmount2 = BigDecimal.valueOf(15.00);
    var withdrawalAmount3 = BigDecimal.valueOf(20.00);

    {
      var command = new DepositUnitEntity.ModifyAmountCommand("deposit-account-1", "deposit-1", "unit-1", amount);
      var result = testKit.call(e -> e.modifyAmount(command));
      assertEquals("OK", result.getReply());
    }

    { // withdraw less than the amount with zero prior withdrawals
      var command = new DepositUnitEntity.WithdrawCommand("withdrawal-account-1", "withdrawal-1", "leaf-1", withdrawalAmount1);
      var result = testKit.call(e -> e.withdraw(command));
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositUnitEntity.WithdrawnEvent.class);
      assertEquals("deposit-account-1", event.depositUnit().accountId());
      assertEquals("deposit-1", event.depositUnit().depositId());
      assertEquals("unit-1", event.depositUnit().unitId());
      assertEquals(0, event.depositUnit().amount().compareTo(amount));
      assertEquals(0, event.depositUnit().balance().compareTo(amount.subtract(withdrawalAmount1)));
      assertEquals(0, event.depositUnit().amountWithdrawn().compareTo(withdrawalAmount1));
      assertEquals("withdrawal-account-1", event.withdrawLeaf().accountId());
      assertEquals("withdrawal-1", event.withdrawLeaf().withdrawalId());
      assertEquals("leaf-1", event.withdrawLeaf().leafId());
      assertEquals(0, event.withdrawLeaf().amount().compareTo(withdrawalAmount1));

      var state = testKit.getState();
      assertEquals("deposit-account-1", state.accountId());
      assertEquals("deposit-1", state.depositId());
      assertEquals("unit-1", state.unitId());
      assertEquals(0, state.amount().compareTo(amount));
      assertEquals(0, state.balance().compareTo(amount.subtract(withdrawalAmount1)));
      assertTrue(startTime.isBefore(state.lastUpdated()));
    }

    { // try to withdraw more than the amount with one prior withdrawal and get the remaining amount minus prior withdrawals
      var command = new DepositUnitEntity.WithdrawCommand("withdrawal-account-2", "withdrawal-2", "leaf-2", withdrawalAmount2);
      var result = testKit.call(e -> e.withdraw(command));
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositUnitEntity.WithdrawnEvent.class);
      assertEquals("deposit-account-1", event.depositUnit().accountId());
      assertEquals("deposit-1", event.depositUnit().depositId());
      assertEquals("unit-1", event.depositUnit().unitId());
      assertEquals(0, event.depositUnit().amount().compareTo(amount));
      assertEquals(0, event.depositUnit().balance().compareTo(BigDecimal.ZERO));
      assertEquals(0, event.depositUnit().amountWithdrawn().compareTo(amount.subtract(withdrawalAmount1)));
      assertEquals("withdrawal-account-2", event.withdrawLeaf().accountId());
      assertEquals("withdrawal-2", event.withdrawLeaf().withdrawalId());
      assertEquals("leaf-2", event.withdrawLeaf().leafId());
      assertEquals(0, event.withdrawLeaf().amount().compareTo(amount.subtract(withdrawalAmount1)));

      var state = testKit.getState();
      assertEquals(0, state.balance().compareTo(BigDecimal.ZERO));
    }

    { // try to withdraw more than the amount with two prior withdrawals have consumed the amount and get zero withdrawn
      var command = new DepositUnitEntity.WithdrawCommand("withdrawal-account-3", "withdrawal-3", "leaf-3", withdrawalAmount3);
      var result = testKit.call(e -> e.withdraw(command));
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositUnitEntity.WithdrawnEvent.class);
      assertEquals("deposit-account-1", event.depositUnit().accountId());
      assertEquals("deposit-1", event.depositUnit().depositId());
      assertEquals("unit-1", event.depositUnit().unitId());
      assertEquals(0, event.depositUnit().amount().compareTo(amount));
      assertEquals(0, event.depositUnit().balance().compareTo(BigDecimal.ZERO));
      assertEquals(0, event.depositUnit().amountWithdrawn().compareTo(BigDecimal.ZERO));
      assertEquals("withdrawal-account-3", event.withdrawLeaf().accountId());
      assertEquals("withdrawal-3", event.withdrawLeaf().withdrawalId());
      assertEquals("leaf-3", event.withdrawLeaf().leafId());
      assertEquals(0, event.withdrawLeaf().amount().compareTo(BigDecimal.ZERO));

      var state = testKit.getState();
      assertEquals(0, state.balance().compareTo(BigDecimal.ZERO));
    }
  }
}
