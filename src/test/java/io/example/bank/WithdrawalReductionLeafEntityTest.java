package io.example.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import io.example.bank.WithdrawalReductionLeafEntity.Event;
import io.example.bank.WithdrawalReductionLeafEntity.State;
import kalix.javasdk.testkit.EventSourcedTestKit;

public class WithdrawalReductionLeafEntityTest {
  @Test
  public void createLeafTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionLeafEntity::new);

    {
      var command = new WithdrawalReductionLeafEntity.LeafCreateCommand("accountId", "withdrawalId", "leafId", BigDecimal.valueOf(10.00));
      var result = testKit.call(e -> e.createLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalReductionLeafEntity.DepositSeekEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals("leafId", event.leafId());
      assertEquals(BigDecimal.valueOf(10.00), event.amountNeeded());

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("withdrawalId", state.withdrawalId());
      assertEquals("leafId", state.leafId());
      assertEquals(BigDecimal.valueOf(10.00), state.amountToWithdraw());
      assertEquals(BigDecimal.ZERO, state.amountWithdrawn());
    }

    { // duplicate command test
      var command = new WithdrawalReductionLeafEntity.LeafCreateCommand("accountId", "withdrawalId", "leafId", BigDecimal.valueOf(10.00));
      var result = testKit.call(e -> e.createLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(0, result.getAllEvents().size());
    }
  }

  @Test
  public void depositFoundFullAmountTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionLeafEntity::new);

    {
      var command = new WithdrawalReductionLeafEntity.LeafCreateCommand("accountId", "withdrawalId", "leafId", BigDecimal.valueOf(10.00));
      var result = testKit.call(e -> e.createLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var depositUnit = new WithdrawalReductionLeafEntity.DepositUnit("account-2", "deposit-1", "unit-1", BigDecimal.valueOf(10.00));
      var command = new WithdrawalReductionLeafEntity.DepositFoundCommand("accountId", "withdrawalId", "leafId", depositUnit);
      var result = testKit.call(e -> e.depositFound(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      {
        var event = result.getNextEventOfType(WithdrawalReductionLeafEntity.DepositFoundEvent.class);
        assertEquals("accountId", event.accountId());
        assertEquals("withdrawalId", event.withdrawalId());
        assertEquals("leafId", event.leafId());
        assertEquals(depositUnit, event.depositUnit());
      }

      {
        var event = result.getNextEventOfType(WithdrawalReductionLeafEntity.FullyFundedEvent.class);
        assertEquals("accountId", event.accountId());
        assertEquals("withdrawalId", event.withdrawalId());
        assertEquals("leafId", event.leafId());
        assertEquals(BigDecimal.valueOf(10.00), event.amount());
      }

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("withdrawalId", state.withdrawalId());
      assertEquals("leafId", state.leafId());
      assertEquals(BigDecimal.valueOf(10.00), state.amountToWithdraw());
      assertEquals(BigDecimal.valueOf(10.00), state.amountWithdrawn());
    }
  }

  @Test
  public void multipleSeeksToGetFullWithdrawalAmountNeededTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionLeafEntity::new);

    {
      var command = new WithdrawalReductionLeafEntity.LeafCreateCommand("accountId", "withdrawalId", "leafId", BigDecimal.valueOf(10.00));
      var result = testKit.call(e -> e.createLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    partiallyFundedSeekResult(testKit);
    partiallyFundedSeekResult(testKit); // duplicate command test

    fullyFundedSeekResult(testKit);
    fullyFundedSeekResult(testKit); // duplicate command test
  }

  @Test
  public void leafNotFoundTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionLeafEntity::new);

    {
      var command = new WithdrawalReductionLeafEntity.LeafCreateCommand("accountId", "withdrawalId", "leafId", BigDecimal.valueOf(10.00));
      var result = testKit.call(e -> e.createLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new WithdrawalReductionLeafEntity.DepositNotFoundCommand("accountId", "withdrawalId", "leafId");
      var result = testKit.call(e -> e.depositNotFound(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalReductionLeafEntity.DepositNotFoundEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals("leafId", event.leafId());

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("withdrawalId", state.withdrawalId());
      assertEquals("leafId", state.leafId());
      assertEquals(BigDecimal.valueOf(10.00), state.amountToWithdraw());
      assertEquals(BigDecimal.ZERO, state.amountWithdrawn());
      assertEquals(0, state.depositUnits().size());
    }
  }

  @Test
  public void cancelWithdrawalTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionLeafEntity::new);

    {
      var command = new WithdrawalReductionLeafEntity.LeafCreateCommand("accountId", "withdrawalId", "leafId", BigDecimal.valueOf(10.00));
      var result = testKit.call(e -> e.createLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    partiallyFundedSeekResult(testKit);
    fullyFundedSeekResult(testKit);

    {
      var command = new WithdrawalReductionLeafEntity.CancelWithdrawalCommand("accountId", "withdrawalId", "leafId");
      var result = testKit.call(e -> e.cancelWithdrawal(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalReductionLeafEntity.CanceledWithdrawalEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals("leafId", event.leafId());
      assertEquals(2, event.depositUnits().size());

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("withdrawalId", state.withdrawalId());
      assertEquals("leafId", state.leafId());
      assertEquals(BigDecimal.valueOf(10.00), state.amountToWithdraw());
      assertEquals(BigDecimal.ZERO, state.amountWithdrawn());
      assertEquals(0, state.depositUnits().size());
    }
  }

  private void partiallyFundedSeekResult(EventSourcedTestKit<State, Event, WithdrawalReductionLeafEntity> testKit) {
    var depositUnit = new WithdrawalReductionLeafEntity.DepositUnit("account-1", "deposit-1", "unit-1", BigDecimal.valueOf(5.00));
    var command = new WithdrawalReductionLeafEntity.DepositFoundCommand("accountId", "withdrawalId", "leafId", depositUnit);
    var result = testKit.call(e -> e.depositFound(command));
    assertTrue(result.isReply());
    assertEquals("OK", result.getReply());
    {
      var event = result.getNextEventOfType(WithdrawalReductionLeafEntity.DepositFoundEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals("leafId", event.leafId());
      assertEquals(depositUnit, event.depositUnit());
    }
    {
      var event = result.getNextEventOfType(WithdrawalReductionLeafEntity.DepositSeekEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals("leafId", event.leafId());
      assertEquals(BigDecimal.valueOf(5.00), event.amountNeeded());
    }
    var state = testKit.getState();
    assertEquals("accountId", state.accountId());
    assertEquals("withdrawalId", state.withdrawalId());
    assertEquals("leafId", state.leafId());
    assertEquals(BigDecimal.valueOf(10.00), state.amountToWithdraw());
    assertEquals(BigDecimal.valueOf(5.00), state.amountWithdrawn());
    assertEquals(1, state.depositUnits().size());
  }

  private void fullyFundedSeekResult(EventSourcedTestKit<State, Event, WithdrawalReductionLeafEntity> testKit) {
    var depositUnit = new WithdrawalReductionLeafEntity.DepositUnit("account-2", "deposit-2", "unit-2", BigDecimal.valueOf(5.00));
    var command = new WithdrawalReductionLeafEntity.DepositFoundCommand("accountId", "withdrawalId", "leafId", depositUnit);
    var result = testKit.call(e -> e.depositFound(command));
    assertTrue(result.isReply());
    assertEquals("OK", result.getReply());
    {
      var event = result.getNextEventOfType(WithdrawalReductionLeafEntity.DepositFoundEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals("leafId", event.leafId());
      assertEquals(depositUnit, event.depositUnit());
    }
    {
      var event = result.getNextEventOfType(WithdrawalReductionLeafEntity.FullyFundedEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals("leafId", event.leafId());
      assertEquals(BigDecimal.valueOf(10.00), event.amount());
    }
    var state = testKit.getState();
    assertEquals("accountId", state.accountId());
    assertEquals("withdrawalId", state.withdrawalId());
    assertEquals("leafId", state.leafId());
    assertEquals(BigDecimal.valueOf(10.00), state.amountToWithdraw());
    assertEquals(BigDecimal.valueOf(10.00), state.amountWithdrawn());
    assertEquals(2, state.depositUnits().size());
  }
}
