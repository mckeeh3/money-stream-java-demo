package io.example.bank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("branchId")
@TypeId("accountRedTree")
@RequestMapping("/accountRedTree/{branchId}")
public class AccountRedTreeEntity extends EventSourcedEntity<AccountRedTreeEntity.State, AccountRedTreeEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(AccountRedTreeEntity.class);
  private final String entityId;

  public AccountRedTreeEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/updateSubbranch")
  public Effect<String> updateSubbranch(@RequestBody UpdateSubbranchCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/releaseBranch")
  public Effect<String> releaseBranch(@RequestBody ReleaseBranchCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @EventHandler
  public State on(UpdatedSubbranchEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedBranchEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedBranchEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      BranchId branchId,
      LocalDateTime lastUpdated,
      boolean hasChanged,
      List<Subbranch> subbranches) {

    static State emptyState() {
      return new State(null, null, false, List.of());
    }

    List<? extends Event> eventsFor(UpdateSubbranchCommand command) {
      if (hasChanged) {
        return List.of(new UpdatedSubbranchEvent(command.branchId(), command.subbranchId(), command.amount()));
      }
      return List.of(
          new UpdatedSubbranchEvent(command.branchId(), command.subbranchId(), command.amount()),
          new UpdatedBranchEvent(command.branchId()));
    }

    Event eventFor(ReleaseBranchCommand command) {
      var zeroSubbranch = new Subbranch(command.branchId(), BigDecimal.ZERO);
      var branch = subbranches.stream()
          .reduce(zeroSubbranch, (a, c) -> new Subbranch(a.subbranchId(), a.amount().add(c.amount())));
      return new ReleasedBranchEvent(command.branchId(), branch);
    }

    State on(UpdatedSubbranchEvent event) {
      var filteredSubbranches = subbranches.stream()
          .filter(subbranch -> !subbranch.subbranchId().equals(event.subbranchId()));
      var newSubbranch = Stream.of(new Subbranch(event.subbranchId(), event.amount()));
      var newSubbranches = Stream.concat(filteredSubbranches, newSubbranch).toList();

      return new State(
          event.branchId().levelUp(),
          LocalDateTime.now(),
          true,
          newSubbranches);
    }

    State on(UpdatedBranchEvent event) {
      return this;
    }

    State on(ReleasedBranchEvent event) {
      return new State(
          branchId,
          LocalDateTime.now(),
          false,
          subbranches);
    }
  }

  public interface Event {}

  public record Subbranch(BranchId subbranchId, BigDecimal amount) {}

  public record UpdateSubbranchCommand(BranchId branchId, BranchId subbranchId, BigDecimal amount) {}

  public record UpdatedSubbranchEvent(BranchId branchId, BranchId subbranchId, BigDecimal amount) implements Event {}

  public record UpdatedBranchEvent(BranchId branchId) implements Event {}

  public record ReleaseBranchCommand(BranchId branchId) {}

  public record ReleasedBranchEvent(BranchId branchId, Subbranch subbranch) implements Event {}

  public record BranchId(String accountId, int level, int branch) {

    static final int treeLevels = 4;
    static final int treeBranchFactor = 100;
    static final int treeLeavesMax = (int) Math.pow(treeBranchFactor, treeLevels);

    static BranchId fromEntityId(String entityId) {
      var parts = entityId.split("_");
      return new BranchId(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    public static BranchId forLeaf(String accountId, String leafEntityId) {
      var leafNo = Math.abs(leafEntityId.hashCode()) % treeLeavesMax;
      return new BranchId(accountId, treeLevels, leafNo);
    }

    String toEntityId() {
      return "%s_%d_%d".formatted(accountId, level, branch);
    }

    BranchId levelUp() {
      if (level == 0) {
        return this;
      }

      var newLevel = level - 1;
      var newBranch = branch / treeBranchFactor;
      return new BranchId(accountId, newLevel, newBranch);
    }
  }
}