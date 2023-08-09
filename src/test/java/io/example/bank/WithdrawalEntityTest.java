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
      var command = new WithdrawalEntity.WithdrawCommand(withdrawalId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalEntity.WithdrawnEvent.class);
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
      var command = new WithdrawalEntity.WithdrawCommand(withdrawalId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new WithdrawalEntity.WithdrawalApprovedCommand(withdrawalId);
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
      assertFalse(state.rejected());
    }
  }

  @Test
  public void rejectTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalEntity::new);

    var withdrawalId = new WithdrawalEntity.WithdrawalId("accountId", "withdrawalId");
    {
      var command = new WithdrawalEntity.WithdrawCommand(withdrawalId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new WithdrawalEntity.WithdrawalRejectedCommand(withdrawalId);
      var result = testKit.call(e -> e.reject(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalEntity.WithdrawalRejectedEvent.class);
      assertEquals(withdrawalId.accountId(), event.withdrawalId().accountId());
      assertEquals(withdrawalId.withdrawalId(), event.withdrawalId().withdrawalId());

      var state = testKit.getState();
      assertEquals(withdrawalId.accountId(), state.withdrawalId().accountId());
      assertEquals(withdrawalId.withdrawalId(), state.withdrawalId().withdrawalId());
      assertEquals(0, state.amount().compareTo(BigDecimal.valueOf(123.45)));
      assertFalse(state.approved());
      assertTrue(state.rejected());
    }
  }

  @Test
  public void getTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalEntity::new);

    var withdrawalId = new WithdrawalEntity.WithdrawalId("accountId", "withdrawalId");
    {
      var command = new WithdrawalEntity.WithdrawCommand(withdrawalId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.withdraw(command));
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
      assertFalse(reply.rejected());
      assertEquals(0, reply.amount().compareTo(BigDecimal.valueOf(123.45)));
    }
  }
}
