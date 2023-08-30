package io.example.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

public class WithdrawalEntityTest {
  @Test
  public void createTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalEntity::new);

    var withdrawalId = new WithdrawalEntity.WithdrawalId("accountId", "withdrawalId");
    {
      var command = new WithdrawalEntity.WithdrawlCreateCommand(withdrawalId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalEntity.WithdrawalCreatedEvent.class);
      assertEquals(withdrawalId.accountId(), event.withdrawalId().accountId());
      assertEquals(withdrawalId.withdrawalId(), event.withdrawalId().withdrawalId());
      assertEquals(0, event.amount().compareTo(BigDecimal.valueOf(123.45)));

      var state = testKit.getState();
      assertEquals(withdrawalId.accountId(), state.withdrawalId().accountId());
      assertEquals(withdrawalId.withdrawalId(), state.withdrawalId().withdrawalId());
      assertEquals(0, state.amount().compareTo(BigDecimal.valueOf(123.45)));
    }
  }

  @Test
  public void approveTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalEntity::new);

    var withdrawalId = new WithdrawalEntity.WithdrawalId("accountId", "withdrawalId");
    {
      var command = new WithdrawalEntity.WithdrawlCreateCommand(withdrawalId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new WithdrawalEntity.WithdrawalApproveCommand(withdrawalId);
      var result = testKit.call(e -> e.approve(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalEntity.WithdrawalApprovedEvent.class);
      assertEquals(withdrawalId.accountId(), event.withdrawalId().accountId());
      assertEquals(withdrawalId.withdrawalId(), event.withdrawalId().withdrawalId());

      var state = testKit.getState();
      assertEquals(withdrawalId.accountId(), state.withdrawalId().accountId());
      assertEquals(withdrawalId.withdrawalId(), state.withdrawalId().withdrawalId());
      assertEquals(0, state.amount().compareTo(BigDecimal.valueOf(123.45)));
      assertTrue(state.approved());
      assertFalse(state.insufficientFunds());
    }
  }

  @Test
  public void rejectTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalEntity::new);

    var withdrawalId = new WithdrawalEntity.WithdrawalId("accountId", "withdrawalId");
    {
      var command = new WithdrawalEntity.WithdrawlCreateCommand(withdrawalId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new WithdrawalEntity.WithdrawalInsufficientFundsCommand(withdrawalId);
      var result = testKit.call(e -> e.insufficientFunds(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalEntity.WithdrawalInsufficientFundsEvent.class);
      assertEquals(withdrawalId.accountId(), event.withdrawalId().accountId());
      assertEquals(withdrawalId.withdrawalId(), event.withdrawalId().withdrawalId());

      var state = testKit.getState();
      assertEquals(withdrawalId.accountId(), state.withdrawalId().accountId());
      assertEquals(withdrawalId.withdrawalId(), state.withdrawalId().withdrawalId());
      assertEquals(0, state.amount().compareTo(BigDecimal.valueOf(123.45)));
      assertFalse(state.approved());
      assertTrue(state.insufficientFunds());
    }
  }

  @Test
  public void getTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalEntity::new);

    var withdrawalId = new WithdrawalEntity.WithdrawalId("accountId", "withdrawalId");
    {
      var command = new WithdrawalEntity.WithdrawlCreateCommand(withdrawalId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var result = testKit.call(e -> e.get());
      assertTrue(result.isReply());

      var reply = result.getReply();
      assertEquals(withdrawalId.accountId(), reply.withdrawalId().accountId());
      assertEquals(withdrawalId.withdrawalId(), reply.withdrawalId().withdrawalId());
      assertFalse(reply.approved());
      assertFalse(reply.insufficientFunds());
      assertEquals(0, reply.amount().compareTo(BigDecimal.valueOf(123.45)));
    }
  }
}
