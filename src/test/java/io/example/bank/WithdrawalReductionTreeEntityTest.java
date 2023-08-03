package io.example.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

public class WithdrawalReductionTreeEntityTest {
  @Test
  public void createTrunkTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionTreeEntity::new);

    {
      var command = new WithdrawalReductionTreeEntity.TrunkCreateCommand("accountId", "withdrawalId", BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createTrunk(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalReductionTreeEntity.BranchCreatedEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertNull(event.parentBranchId());
      assertNotNull(event.branchId());
      assertEquals(0, event.amount().compareTo(BigDecimal.valueOf(123.45)));
      assertTrue(event.subbranches().size() > 0);

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("withdrawalId", state.withdrawalId());
      assertNull(state.parentBranchId());
      assertNotNull(state.branchId());
      assertEquals(0, state.amountToWithdraw().compareTo(BigDecimal.valueOf(123.45)));
      assertEquals(0, state.amountWithdrawn().compareTo(BigDecimal.ZERO));
      assertTrue(state.subbranches().size() > 0);
    }
  }

  @Test
  public void createBranchTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionTreeEntity::new);

    {
      var command = new WithdrawalReductionTreeEntity.BranchCreateCommand("accountId", "withdrawalId", "parentBranchId", "branchId", BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalReductionTreeEntity.BranchCreatedEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertNotNull(event.parentBranchId());
      assertNotNull(event.branchId());
      assertEquals(0, event.amount().compareTo(BigDecimal.valueOf(123.45)));
      assertTrue(event.subbranches().size() > 0);

      var state = testKit.getState();
      assertEquals("accountId", state.accountId());
      assertEquals("withdrawalId", state.withdrawalId());
      assertEquals("parentBranchId", state.parentBranchId());
      assertNotNull(state.branchId());
      assertEquals(0, state.amountToWithdraw().compareTo(BigDecimal.valueOf(123.45)));
      assertEquals(0, state.amountWithdrawn().compareTo(BigDecimal.ZERO));
      assertTrue(state.subbranches().size() > 0);
    }
  }

  @Test
  public void updateAmountWithdrawnTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionTreeEntity::new);

    {
      var command = new WithdrawalReductionTreeEntity.BranchCreateCommand("accountId", "withdrawalId", "parentBranchId", "branchId", BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var reply = testKit.call(e -> e.get()).getReply();
      var subbranches = reply.subbranches();
      var branchId = subbranches.get(0).branchId();

      var subbranch = new WithdrawalReductionTreeEntity.Subbranch("accountId", "withdrawalId", branchId, BigDecimal.valueOf(123.45), BigDecimal.valueOf(123.45));
      var command = new WithdrawalReductionTreeEntity.UpdateAmountWithdrawnCommand("accountId", "withdrawalId", "branchId", subbranch);
      var result = testKit.call(e -> e.updateAmountWithdrawn(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalReductionTreeEntity.UpdatedAmountWithdrawnEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals("parentBranchId", event.parentBranchId());
      assertEquals("branchId", event.branchId());
      assertEquals(0, event.subbranch().amountWithdrawn().compareTo(BigDecimal.valueOf(123.45)));

      var state = testKit.getState();
      assertEquals(0, state.amountWithdrawn().compareTo(BigDecimal.valueOf(123.45)));
      assertTrue(state.isApproved());
    }
  }

  @Test
  public void multiUpdateAmountWithdrawnTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionTreeEntity::new);

    {
      var command = new WithdrawalReductionTreeEntity.BranchCreateCommand("accountId", "withdrawalId", "parentBranchId", "branchId", BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var reply = testKit.call(e -> e.get()).getReply();
      var subbranches = reply.subbranches();
      var branchId = subbranches.get(0).branchId(); // 1st subbranch

      var subbranch = new WithdrawalReductionTreeEntity.Subbranch("accountId", "withdrawalId", branchId, BigDecimal.valueOf(123.45), BigDecimal.valueOf(100.00));
      var command = new WithdrawalReductionTreeEntity.UpdateAmountWithdrawnCommand("accountId", "withdrawalId", "branchId", subbranch);
      var result = testKit.call(e -> e.updateAmountWithdrawn(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalReductionTreeEntity.UpdatedAmountWithdrawnEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals("parentBranchId", event.parentBranchId());
      assertEquals("branchId", event.branchId());
      assertEquals(0, event.subbranch().amountWithdrawn().compareTo(BigDecimal.valueOf(100.00)));

      var state = testKit.getState();
      assertEquals(0, state.amountWithdrawn().compareTo(BigDecimal.valueOf(100.00)));
      assertFalse(state.isApproved());
    }

    {
      var reply = testKit.call(e -> e.get()).getReply();
      var subbranches = reply.subbranches();
      var branchId = subbranches.get(1).branchId(); // 2nd subbranch

      var subbranch = new WithdrawalReductionTreeEntity.Subbranch("accountId", "withdrawalId", branchId, BigDecimal.valueOf(123.45), BigDecimal.valueOf(23.45));
      var command = new WithdrawalReductionTreeEntity.UpdateAmountWithdrawnCommand("accountId", "withdrawalId", "branchId", subbranch);
      var result = testKit.call(e -> e.updateAmountWithdrawn(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalReductionTreeEntity.UpdatedAmountWithdrawnEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals("parentBranchId", event.parentBranchId());
      assertEquals("branchId", event.branchId());
      assertEquals(0, event.subbranch().amountWithdrawn().compareTo(BigDecimal.valueOf(23.45)));

      var state = testKit.getState();
      assertEquals(0, state.amountWithdrawn().compareTo(BigDecimal.valueOf(123.45)));
      assertTrue(state.isApproved());
    }
  }

  @Test
  public void insufficientFundsMidBranchTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionTreeEntity::new);

    {
      var command = new WithdrawalReductionTreeEntity.BranchCreateCommand("accountId", "withdrawalId", "parentBranchId", "branchId", BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new WithdrawalReductionTreeEntity.InsufficientFundsCommand("accountId", "withdrawalId", "branchId");
      var result = testKit.call(e -> e.insufficientFunds(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalReductionTreeEntity.InsufficientFundsEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertEquals("parentBranchId", event.parentBranchId());
      assertEquals("branchId", event.branchId());

      var state = testKit.getState();
      assertTrue(state.isCanceled());
    }
  }

  @Test
  public void insufficientFundsOnTrunkTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionTreeEntity::new);

    {
      var command = new WithdrawalReductionTreeEntity.TrunkCreateCommand("accountId", "withdrawalId", BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createTrunk(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new WithdrawalReductionTreeEntity.InsufficientFundsCommand("accountId", "withdrawalId", "branchId");
      var result = testKit.call(e -> e.insufficientFunds(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalReductionTreeEntity.CanceledWithdrawalEvent.class);
      assertEquals("accountId", event.accountId());
      assertEquals("withdrawalId", event.withdrawalId());
      assertNull(event.parentBranchId());
      assertTrue(event.subbranchIds().size() > 0);

      var state = testKit.getState();
      assertTrue(state.isCanceled());
    }
  }

  @Test
  public void getTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalReductionTreeEntity::new);

    {
      var command = new WithdrawalReductionTreeEntity.TrunkCreateCommand("accountId", "withdrawalId", BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createTrunk(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var result = testKit.call(e -> e.get());
      assertTrue(result.isReply());

      var reply = result.getReply();
      assertEquals("accountId", reply.accountId());
      assertEquals("withdrawalId", reply.withdrawalId());
      assertNull(reply.parentBranchId());
      assertNotNull(reply.branchId());
      assertEquals(0, reply.amountToWithdraw().compareTo(BigDecimal.valueOf(123.45)));
      assertEquals(0, reply.amountWithdrawn().compareTo(BigDecimal.ZERO));
      assertTrue(reply.subbranches().size() > 0);
    }
  }
}
