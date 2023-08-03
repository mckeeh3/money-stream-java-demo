package io.example.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

public class DepositEntityTest {
  @Test
  public void createDepositTest() {
    var testKit = EventSourcedTestKit.of(DepositEntity::new);

    {
      var amount = BigDecimal.valueOf(123.45);
      var command = new DepositEntity.DepositCommand("accountId", "depositId", amount);
      var result = testKit.call(e -> e.deposit(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(DepositEntity.DepositedEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("depositId", event.depositId());
      assertEquals(0, event.amount().compareTo(amount));

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("depositId", state.depositId());
      assertEquals(0, state.amount().compareTo(amount));
    }
  }

  @Test
  public void getDepositTest() {
    var testKit = EventSourcedTestKit.of(DepositEntity::new);

    {
      var amount = BigDecimal.valueOf(123.45);
      var command = new DepositEntity.DepositCommand("accountId", "depositId", amount);
      var result = testKit.call(e -> e.deposit(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var result = testKit.call(e -> e.get());
      assertTrue(result.isReply());

      var reply = result.getReply();
      assertEquals("accountId", reply.accountId());
      assertEquals("depositId", reply.depositId());
      assertEquals(0, reply.amount().compareTo(BigDecimal.valueOf(123.45)));
    }
  }
}
