package io.example.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

public class AccountEntityTest {
  @Test
  public void createAccountTest() {
    var testKit = EventSourcedTestKit.of(AccountEntity::new);

    var startTime = LocalDateTime.now();

    {
      var command = new AccountEntity.CreateAccountCommand("accountId", "fullName", "address");
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(AccountEntity.CreatedAccountEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("fullName", event.fullName());
      assertEquals("address", event.address());

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("fullName", state.fullName());
      assertEquals("address", state.address());
      assertEquals(0, state.balance().compareTo(BigDecimal.ZERO));
      assertTrue(startTime.isBefore(state.lastUpdated()));
    }
  }

  @Test
  public void createAccountIdempotentTest() {
    var testKit = EventSourcedTestKit.of(AccountEntity::new);

    {
      var command = new AccountEntity.CreateAccountCommand("accountId", "fullName", "address");
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new AccountEntity.CreateAccountCommand("accountId", "fullName", "address");
      var result = testKit.call(e -> e.create(command));
      assertEquals("OK", result.getReply());

      assertEquals(0, result.getAllEvents().size());
    }
  }

  @Test
  public void updateBalanceTest() {
    var testKit = EventSourcedTestKit.of(AccountEntity::new);

    var startTime = LocalDateTime.now();

    {
      var command = new AccountEntity.CreateAccountCommand("accountId", "fullName", "address");
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var result = testKit.call(e -> e.updateBalance(new AccountEntity.UpdateAccountBalanceCommand("accountId", BigDecimal.TEN)));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(AccountEntity.UpdatedAccountBalanceEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals(0, event.balance().compareTo(BigDecimal.TEN));

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("fullName", state.fullName());
      assertEquals("address", state.address());
      assertEquals(0, state.balance().compareTo(BigDecimal.TEN));
      assertTrue(startTime.isBefore(state.lastUpdated()));
    }
  }

  @Test
  public void getAccountTest() {
    var testKit = EventSourcedTestKit.of(AccountEntity::new);

    var startTime = LocalDateTime.now();

    {
      var command = new AccountEntity.CreateAccountCommand("accountId", "fullName", "address");
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var result = testKit.call(e -> e.get());
      assertTrue(result.isReply());

      var reply = result.getReply();
      assertEquals("accountId", reply.accountId());
      assertEquals("fullName", reply.fullName());
      assertEquals("address", reply.address());
      assertEquals(0, reply.balance().compareTo(BigDecimal.ZERO));
      assertTrue(startTime.isBefore(reply.lastUpdated()));
    }

    {
      var command = new AccountEntity.UpdateAccountBalanceCommand("accountId", BigDecimal.TEN);
      var result = testKit.call(e -> e.updateBalance(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var result = testKit.call(e -> e.get());
      assertTrue(result.isReply());

      var reply = result.getReply();
      assertEquals("accountId", reply.accountId());
      assertEquals("fullName", reply.fullName());
      assertEquals("address", reply.address());
      assertEquals(0, reply.balance().compareTo(BigDecimal.TEN));
      assertTrue(startTime.isBefore(reply.lastUpdated()));
    }
  }
}
