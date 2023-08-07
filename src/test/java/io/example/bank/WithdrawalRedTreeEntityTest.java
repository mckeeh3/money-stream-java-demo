package io.example.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

public class WithdrawalRedTreeEntityTest {
  @Test
  public void createTrunkTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalRedTreeEntity::new);

    var withdrawalRedTreeId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "trunkId");
    {
      var command = new WithdrawalRedTreeEntity.TrunkCreateCommand(withdrawalRedTreeId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createTrunk(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalRedTreeEntity.BranchCreatedEvent.class);
      assertEquals(withdrawalRedTreeId, event.withdrawalRedTreeId());
      assertNull(event.withdrawalRedTreeParentId());
      assertEquals(0, event.amount().compareTo(BigDecimal.valueOf(123.45)));
      assertTrue(event.subbranches().size() > 0);

      var state = testKit.getState();
      assertEquals(withdrawalRedTreeId, state.withdrawalRedTreeId());
      assertNull(state.withdrawalRedTreeParentId());
      assertEquals(0, state.amountToWithdraw().compareTo(BigDecimal.valueOf(123.45)));
      assertEquals(0, state.amountWithdrawn().compareTo(BigDecimal.ZERO));
      assertTrue(state.subbranches().size() > 0);
    }
  }

  @Test
  public void createBranchTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalRedTreeEntity::new);

    var withdrawalRedTreeId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "branchId");
    var parentBranchId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "parentBranchId");
    {
      var command = new WithdrawalRedTreeEntity.BranchCreateCommand(withdrawalRedTreeId, parentBranchId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalRedTreeEntity.BranchCreatedEvent.class);
      assertEquals(parentBranchId, event.withdrawalRedTreeParentId());
      assertEquals(0, event.amount().compareTo(BigDecimal.valueOf(123.45)));
      assertTrue(event.subbranches().size() > 0);

      var state = testKit.getState();
      assertNotNull(state.withdrawalRedTreeId());
      assertNotNull(state.withdrawalRedTreeParentId());
      assertEquals(0, state.amountToWithdraw().compareTo(BigDecimal.valueOf(123.45)));
      assertEquals(0, state.amountWithdrawn().compareTo(BigDecimal.ZERO));
      assertTrue(state.subbranches().size() > 0);
    }
  }

  @Test
  public void updateAmountWithdrawnTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalRedTreeEntity::new);

    var withdrawalRedTreeId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "branchId");
    var parentBranchId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "parentBranchId");
    {
      var command = new WithdrawalRedTreeEntity.BranchCreateCommand(withdrawalRedTreeId, parentBranchId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var reply = testKit.call(e -> e.get()).getReply();
      var subbranches = reply.subbranches();
      var branchId = subbranches.get(0).withdrawalRedTreeId();

      var subbranchId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", branchId.branchId());
      var subbranch = new WithdrawalRedTreeEntity.Subbranch(subbranchId, BigDecimal.valueOf(123.45), BigDecimal.valueOf(123.45));
      var command = new WithdrawalRedTreeEntity.UpdateAmountWithdrawnCommand(withdrawalRedTreeId, subbranch);
      var result = testKit.call(e -> e.updateAmountWithdrawn(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalRedTreeEntity.UpdatedAmountWithdrawnEvent.class);
      // assertEquals(withdrawalRedTreeId, event.withdrawalRedTreeId());
      assertEquals(0, event.subbranch().amountWithdrawn().compareTo(BigDecimal.valueOf(123.45)));

      var state = testKit.getState();
      assertEquals(0, state.amountWithdrawn().compareTo(BigDecimal.valueOf(123.45)));
      assertTrue(state.isApproved());
    }
  }

  @Test
  public void multiUpdateAmountWithdrawnTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalRedTreeEntity::new);

    var withdrawalRedTreeId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "branchId");
    var parentBranchId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "parentBranchId");
    {
      var command = new WithdrawalRedTreeEntity.BranchCreateCommand(withdrawalRedTreeId, parentBranchId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var reply = testKit.call(e -> e.get()).getReply();
      var subbranches = reply.subbranches();
      var branchId = subbranches.get(0).withdrawalRedTreeId(); // 1st subbranch

      var subbranchId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", branchId.branchId());
      var subbranch = new WithdrawalRedTreeEntity.Subbranch(subbranchId, BigDecimal.valueOf(123.45), BigDecimal.valueOf(100.00));
      var command = new WithdrawalRedTreeEntity.UpdateAmountWithdrawnCommand(withdrawalRedTreeId, subbranch);
      var result = testKit.call(e -> e.updateAmountWithdrawn(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalRedTreeEntity.UpdatedAmountWithdrawnEvent.class);
      // assertEquals(withdrawalRedTreeId, event.withdrawalRedTreeId());
      assertEquals(0, event.subbranch().amountWithdrawn().compareTo(BigDecimal.valueOf(100.00)));

      var state = testKit.getState();
      assertEquals(0, state.amountWithdrawn().compareTo(BigDecimal.valueOf(100.00)));
      assertFalse(state.isApproved());
    }

    {
      var reply = testKit.call(e -> e.get()).getReply();
      var subbranches = reply.subbranches();
      var branchId = subbranches.get(1).withdrawalRedTreeId(); // 2nd subbranch

      var subbranchId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", branchId.branchId());
      var subbranch = new WithdrawalRedTreeEntity.Subbranch(subbranchId, BigDecimal.valueOf(123.45), BigDecimal.valueOf(23.45));
      var command = new WithdrawalRedTreeEntity.UpdateAmountWithdrawnCommand(withdrawalRedTreeId, subbranch);
      var result = testKit.call(e -> e.updateAmountWithdrawn(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalRedTreeEntity.UpdatedAmountWithdrawnEvent.class);
      // assertEquals(withdrawalRedTreeId, event.withdrawalRedTreeId());
      assertEquals(0, event.subbranch().amountWithdrawn().compareTo(BigDecimal.valueOf(23.45)));

      var state = testKit.getState();
      assertEquals(0, state.amountWithdrawn().compareTo(BigDecimal.valueOf(123.45)));
      assertTrue(state.isApproved());
    }
  }

  @Test
  public void insufficientFundsMidBranchTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalRedTreeEntity::new);

    var withdrawalRedTreeId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "branchId");
    var parentBranchId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "parentBranchId");
    {
      var command = new WithdrawalRedTreeEntity.BranchCreateCommand(withdrawalRedTreeId, parentBranchId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new WithdrawalRedTreeEntity.InsufficientFundsCommand(withdrawalRedTreeId);
      var result = testKit.call(e -> e.insufficientFunds(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalRedTreeEntity.InsufficientFundsEvent.class);
      // assertEquals(withdrawalRedTreeId, event.withdrawalRedTreeId());
      assertEquals(parentBranchId, event.withdrawalRedTreeParentId());

      var state = testKit.getState();
      assertTrue(state.isCanceled());
    }
  }

  @Test
  public void insufficientFundsOnTrunkTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalRedTreeEntity::new);

    var trunkId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "trunkId");
    {
      var command = new WithdrawalRedTreeEntity.TrunkCreateCommand(trunkId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createTrunk(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var branchId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "trunkId");
      var command = new WithdrawalRedTreeEntity.InsufficientFundsCommand(branchId);
      var result = testKit.call(e -> e.insufficientFunds(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(WithdrawalRedTreeEntity.CanceledWithdrawalEvent.class);
      assertEquals(trunkId, event.withdrawalRedTreeIId());
      assertTrue(event.subbranchIds().size() > 0);

      var state = testKit.getState();
      assertTrue(state.isCanceled());
    }
  }

  @Test
  public void getTest() {
    var testKit = EventSourcedTestKit.of(WithdrawalRedTreeEntity::new);

    var withdrawalRedTreeId = new WithdrawalRedTreeEntity.WithdrawalRedTreeId("accountId", "withdrawalId", "trunkId");
    {
      var command = new WithdrawalRedTreeEntity.TrunkCreateCommand(withdrawalRedTreeId, BigDecimal.valueOf(123.45));
      var result = testKit.call(e -> e.createTrunk(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var result = testKit.call(e -> e.get());
      assertTrue(result.isReply());

      var reply = result.getReply();
      assertEquals(withdrawalRedTreeId, reply.withdrawalRedTreeId());
      assertNull(reply.withdrawalRedTreeParentId());
      assertEquals(0, reply.amountToWithdraw().compareTo(BigDecimal.valueOf(123.45)));
      assertEquals(0, reply.amountWithdrawn().compareTo(BigDecimal.ZERO));
      assertTrue(reply.subbranches().size() > 0);
    }
  }
}
