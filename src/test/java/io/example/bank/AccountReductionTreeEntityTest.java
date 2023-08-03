package io.example.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

public class AccountReductionTreeEntityTest {
  @Test
  public void updateSubBranchOfTrunkTest() {
    var testKit = EventSourcedTestKit.of(AccountReductionTreeEntity::new);

    var startTime = LocalDateTime.now();
    var branchId = AccountReductionTreeEntity.BranchId.fromEntityId("accountId_0_0");
    var subbranchId = AccountReductionTreeEntity.BranchId.fromEntityId("accountId_1_0");

    {
      var command = new AccountReductionTreeEntity.UpdateSubbranchCommand(branchId, subbranchId, BigDecimal.TEN);
      var result = testKit.call(e -> e.updateSubbranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(2, result.getAllEvents().size());

      var event1 = result.getNextEventOfType(AccountReductionTreeEntity.UpdatedSubbranchEvent.class);
      assertEquals(branchId, event1.branchId());
      assertEquals(subbranchId, event1.subbranchId());
      assertEquals(0, event1.amount().compareTo(BigDecimal.TEN));

      var event2 = result.getNextEventOfType(AccountReductionTreeEntity.UpdatedBranchEvent.class);
      assertEquals(branchId, event2.branchId());

      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(1, state.subbranches().size());
      assertTrue(startTime.isBefore(state.lastUpdated()));
      assertTrue(state.hasChanged());
    }
  }

  @Test
  public void updateMultipleSubBranches() {
    var testKit = EventSourcedTestKit.of(AccountReductionTreeEntity::new);

    var startTime = LocalDateTime.now();
    var branchId = AccountReductionTreeEntity.BranchId.fromEntityId("accountId_0_0");
    var subbranchId1 = AccountReductionTreeEntity.BranchId.fromEntityId("accountId_1_0");
    var subbranchId2 = AccountReductionTreeEntity.BranchId.fromEntityId("accountId_1_1");

    {
      var command = new AccountReductionTreeEntity.UpdateSubbranchCommand(branchId, subbranchId1, BigDecimal.ONE);
      var result = testKit.call(e -> e.updateSubbranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(2, result.getAllEvents().size());

      var event1 = result.getNextEventOfType(AccountReductionTreeEntity.UpdatedSubbranchEvent.class);
      assertEquals(branchId, event1.branchId());

      assertEquals(subbranchId1, event1.subbranchId());
      assertEquals(0, event1.amount().compareTo(BigDecimal.ONE));

      var event2 = result.getNextEventOfType(AccountReductionTreeEntity.UpdatedBranchEvent.class);
      assertEquals(branchId.levelUp(), event2.branchId());

      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(1, state.subbranches().size());
      assertEquals(new AccountReductionTreeEntity.Subbranch(subbranchId1, BigDecimal.ONE), state.subbranches().get(0));
      assertTrue(startTime.isBefore(state.lastUpdated()));
      assertTrue(state.hasChanged());
    }

    {
      var command = new AccountReductionTreeEntity.UpdateSubbranchCommand(branchId, subbranchId2, BigDecimal.TEN);
      var result = testKit.call(e -> e.updateSubbranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(AccountReductionTreeEntity.UpdatedSubbranchEvent.class);
      assertEquals(branchId, event.branchId());
      assertEquals(subbranchId2, event.subbranchId());
      assertEquals(0, event.amount().compareTo(BigDecimal.TEN));

      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(2, state.subbranches().size());
      assertEquals(new AccountReductionTreeEntity.Subbranch(subbranchId1, BigDecimal.ONE), state.subbranches().get(0));
      assertEquals(new AccountReductionTreeEntity.Subbranch(subbranchId2, BigDecimal.TEN), state.subbranches().get(1));
      assertTrue(startTime.isBefore(state.lastUpdated()));
      assertTrue(state.hasChanged());
    }
  }

  @Test
  public void releaseBranchTest() {
    var testKit = EventSourcedTestKit.of(AccountReductionTreeEntity::new);

    var startTime = LocalDateTime.now();
    var branchId = AccountReductionTreeEntity.BranchId.fromEntityId("accountId_0_0");
    var subbranchId1 = AccountReductionTreeEntity.BranchId.fromEntityId("accountId_1_0");
    var subbranchId2 = AccountReductionTreeEntity.BranchId.fromEntityId("accountId_1_1");

    {
      var command = new AccountReductionTreeEntity.UpdateSubbranchCommand(branchId, subbranchId1, BigDecimal.TEN);
      var result = testKit.call(e -> e.updateSubbranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new AccountReductionTreeEntity.UpdateSubbranchCommand(branchId, subbranchId2, BigDecimal.TEN);
      var result = testKit.call(e -> e.updateSubbranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new AccountReductionTreeEntity.ReleaseBranchCommand(branchId);
      var result = testKit.call(e -> e.releaseBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(AccountReductionTreeEntity.ReleasedBranchEvent.class);
      assertEquals(branchId, event.branchId());
      assertEquals((BigDecimal.TEN).add(BigDecimal.TEN), event.subbranch().amount());

      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(2, state.subbranches().size());
      assertEquals(new AccountReductionTreeEntity.Subbranch(subbranchId1, BigDecimal.TEN), state.subbranches().get(0));
      assertEquals(new AccountReductionTreeEntity.Subbranch(subbranchId2, BigDecimal.TEN), state.subbranches().get(1));
      assertTrue(startTime.isBefore(state.lastUpdated()));
      assertFalse(state.hasChanged());
    }
  }
}
