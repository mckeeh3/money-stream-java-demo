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
      var depositUnitId = new DepositUnitEntity.DepositUnitId("deposit-account-1", "deposit-1", "unit-1");
      var command = new DepositUnitEntity.ModifyAmountCommand(depositUnitId, BigDecimal.valueOf(543.21));
      var result = testKit.call(e -> e.modifyAmount(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositUnitEntity.ModifiedAmountEvent.class);
      assertEquals(depositUnitId, event.depositUnitId());
      assertEquals(0, event.amount().compareTo(BigDecimal.valueOf(543.21)));

      var modifyAmounts = event.modifyAmounts();
      assertTrue(modifyAmounts.size() > 1);
      assertEquals(depositUnitId, modifyAmounts.get(0).depositUnitId());
      assertEquals(-1, modifyAmounts.get(0).amount().compareTo(BigDecimal.valueOf(543.21)));

      var state = testKit.getState();
      assertEquals(depositUnitId, state.depositUnitId());
      assertEquals(0, state.amount().compareTo(BigDecimal.valueOf(543.21)));
      assertTrue(startTime.isBefore(state.lastUpdated()));
    }
  }

  @Test
  public void duplicateModifyAmountCommandTest() {
    var testKit = EventSourcedTestKit.of(DepositUnitEntity::new);

    var depositUnitId = new DepositUnitEntity.DepositUnitId("deposit-account-1", "deposit-1", "unit-1");
    var command = new DepositUnitEntity.ModifyAmountCommand(depositUnitId, BigDecimal.valueOf(543.21));

    {
      var result = testKit.call(e -> e.modifyAmount(command));
      assertTrue(result.isReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var result = testKit.call(e -> e.modifyAmount(command));
      assertTrue(result.isReply());
      assertEquals(0, result.getAllEvents().size());
    }
  }

  @Test
  public void setAmountLow() {
    var testKit = EventSourcedTestKit.of(DepositUnitEntity::new);

    var startTime = LocalDateTime.now();

    {
      var depositUnitId = new DepositUnitEntity.DepositUnitId("accountId", "depositId", "unitId");
      var command = new DepositUnitEntity.ModifyAmountCommand(depositUnitId, BigDecimal.valueOf(12.34));
      var result = testKit.call(e -> e.modifyAmount(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositUnitEntity.ModifiedAmountEvent.class);
      assertEquals(depositUnitId, event.depositUnitId());
      assertEquals(0, event.amount().compareTo(BigDecimal.valueOf(12.34)));

      var modifyAmounts = event.modifyAmounts();
      assertEquals(0, modifyAmounts.size());

      var state = testKit.getState();
      assertEquals(depositUnitId, state.depositUnitId());
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

    var depositUnitId = new DepositUnitEntity.DepositUnitId("deposit-account-1", "deposit-1", "unit-1");
    {
      var command = new DepositUnitEntity.ModifyAmountCommand(depositUnitId, amount);
      var result = testKit.call(e -> e.modifyAmount(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // withdraw less than the amount with zero prior withdrawals
      var withdrawalRedLeafId = new WithdrawalRedLeafEntity.WithdrawalRedLeafId("withdrawal-account-1", "withdrawal-1", "leaf-1");
      var command = new DepositUnitEntity.WithdrawCommand(withdrawalRedLeafId, withdrawalAmount1);
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositUnitEntity.WithdrawnEvent.class);
      assertEquals(depositUnitId, event.depositUnit().depositUnitId());
      assertEquals(0, event.depositUnit().amount().compareTo(amount));
      assertEquals(0, event.depositUnit().balance().compareTo(amount.subtract(withdrawalAmount1)));
      assertEquals(0, event.depositUnit().amountWithdrawn().compareTo(withdrawalAmount1));
      assertEquals(withdrawalRedLeafId, event.withdrawalRedLeafId());

      var state = testKit.getState();
      assertEquals(depositUnitId, state.depositUnitId());
      assertEquals(0, state.amount().compareTo(amount));
      assertEquals(0, state.balance().compareTo(amount.subtract(withdrawalAmount1)));
      assertTrue(startTime.isBefore(state.lastUpdated()));
    }

    { // try to withdraw more than the amount with one prior withdrawal and get the remaining amount minus prior withdrawals
      var withdrawalRedLeafId = new WithdrawalRedLeafEntity.WithdrawalRedLeafId("withdrawal-account-2", "withdrawal-2", "leaf-2");
      var command = new DepositUnitEntity.WithdrawCommand(withdrawalRedLeafId, withdrawalAmount2);
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositUnitEntity.WithdrawnEvent.class);
      assertEquals(depositUnitId, event.depositUnit().depositUnitId());
      assertEquals(0, event.depositUnit().amount().compareTo(amount));
      assertEquals(0, event.depositUnit().balance().compareTo(BigDecimal.ZERO));
      assertEquals(0, event.depositUnit().amountWithdrawn().compareTo(amount.subtract(withdrawalAmount1)));
      assertEquals(withdrawalRedLeafId, event.withdrawalRedLeafId());

      var state = testKit.getState();
      assertEquals(0, state.balance().compareTo(BigDecimal.ZERO));
    }

    { // try to withdraw more than the amount with two prior withdrawals have consumed the amount and get zero withdrawn
      var withdrawalRedLeafId = new WithdrawalRedLeafEntity.WithdrawalRedLeafId("withdrawal-account-3", "withdrawal-3", "leaf-3");
      var command = new DepositUnitEntity.WithdrawCommand(withdrawalRedLeafId, withdrawalAmount3);
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositUnitEntity.WithdrawnEvent.class);
      assertEquals(depositUnitId, event.depositUnit().depositUnitId());
      assertEquals(0, event.depositUnit().amount().compareTo(amount));
      assertEquals(0, event.depositUnit().balance().compareTo(BigDecimal.ZERO));
      assertEquals(0, event.depositUnit().amountWithdrawn().compareTo(BigDecimal.ZERO));
      assertEquals(withdrawalRedLeafId, event.withdrawalRedLeafId());

      var state = testKit.getState();
      assertEquals(0, state.balance().compareTo(BigDecimal.ZERO));
    }
  }

  @Test
  public void cancelWithdrawalTest() {
    var testKit = EventSourcedTestKit.of(DepositUnitEntity::new);

    var amount = BigDecimal.valueOf(12.34);
    var withdrawalAmount1 = BigDecimal.valueOf(2.34);
    var withdrawalAmount2 = BigDecimal.valueOf(5.00);
    var withdrawalAmount3 = BigDecimal.valueOf(5.00);

    var depositUnitId = new DepositUnitEntity.DepositUnitId("deposit-account-1", "deposit-1", "unit-1");
    var withdrawalRedLeafId1 = new WithdrawalRedLeafEntity.WithdrawalRedLeafId("withdrawal-account-1", "withdrawal-1", "leaf-1");
    var withdrawalRedLeafId2 = new WithdrawalRedLeafEntity.WithdrawalRedLeafId("withdrawal-account-2", "withdrawal-2", "leaf-2");
    var withdrawalRedLeafId3 = new WithdrawalRedLeafEntity.WithdrawalRedLeafId("withdrawal-account-3", "withdrawal-3", "leaf-3");

    {
      var command = new DepositUnitEntity.ModifyAmountCommand(depositUnitId, amount);
      var result = testKit.call(e -> e.modifyAmount(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new DepositUnitEntity.WithdrawCommand(withdrawalRedLeafId1, withdrawalAmount1);
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new DepositUnitEntity.WithdrawCommand(withdrawalRedLeafId2, withdrawalAmount2);
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // after the third withdrawal the deposit balance must be zero
      var command = new DepositUnitEntity.WithdrawCommand(withdrawalRedLeafId3, withdrawalAmount3);
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var state = testKit.getState();
      assertEquals(0, state.balance().compareTo(BigDecimal.ZERO));
      assertEquals(3, state.withdrawals().size());
    }

    { // cancel the second withdrawal and the deposit balance must be the amount of the second withdrawal
      var command = new DepositUnitEntity.WithdrawalCancelCommand(depositUnitId, withdrawalRedLeafId2);
      var result = testKit.call(e -> e.cancelWithdrawal(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositUnitEntity.WithdrawalCancelledEvent.class);
      assertEquals(withdrawalRedLeafId2, event.withdrawalRedLeafId());

      var state = testKit.getState();
      assertEquals(0, state.balance().compareTo(withdrawalAmount2));
      assertEquals(2, state.withdrawals().size());
    }
  }
}
