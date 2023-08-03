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

    {
      var command = new WithdrawalEntity.WithdrawCommand("accountId", "withdrawalId", BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalEntity.WithdrawnEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals(0, event.amount().compareTo(BigDecimal.valueOf(123.45)));

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("withdrawalId", state.withdrawalId());
      assertEquals(0, state.amount().compareTo(BigDecimal.valueOf(123.45)));
    }
  }

  @Test
  public void approveTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalEntity::new);

    {
      var command = new WithdrawalEntity.WithdrawCommand("accountId", "withdrawalId", BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new WithdrawalEntity.WithdrawalApprovedCommand("accountId", "withdrawalId");
      var result = testKit.call(e -> e.approve(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalEntity.WithdrawalApprovedEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("withdrawalId", state.withdrawalId());
      assertEquals(0, state.amount().compareTo(BigDecimal.valueOf(123.45)));
      assertTrue(state.approved());
      assertFalse(state.rejected());
    }
  }

  @Test
  public void rejectTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalEntity::new);

    {
      var command = new WithdrawalEntity.WithdrawCommand("accountId", "withdrawalId", BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new WithdrawalEntity.WithdrawalRejectedCommand("accountId", "withdrawalId");
      var result = testKit.call(e -> e.reject(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalEntity.WithdrawalRejectedEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("withdrawalId", state.withdrawalId());
      assertEquals(0, state.amount().compareTo(BigDecimal.valueOf(123.45)));
      assertFalse(state.approved());
      assertTrue(state.rejected());
    }
  }

  @Test
  public void getTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalEntity::new);

    {
      var command = new WithdrawalEntity.WithdrawCommand("accountId", "withdrawalId", BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.withdraw(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var result = testKit.call(e -> e.get());
      assertTrue(result.isReply());

      var reply = result.getReply();
      assertEquals("accountId", reply.accountId());
      assertEquals("withdrawalId", reply.withdrawalId());
      assertFalse(reply.approved());
      assertFalse(reply.rejected());
      assertEquals(0, reply.amount().compareTo(BigDecimal.valueOf(123.45)));
    }
  }
}
