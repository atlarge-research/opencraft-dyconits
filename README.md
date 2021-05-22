
# Dyconits

Dyconits (Dynamic Consistency Units) is a library that allows _dynamic consistency tradeoffs_ in real-time applications
such as online games.

## Add Dyconits to Your Project

You can add dyconits as a Maven dependency.

Add the Opencraft Maven repository:

```
<repositories>
    <repository>
        <id>opencraft-group</id>
        <url>https://opencraft-vm.labs.vu.nl/nexus/repository/opencraft-group/</url>
    </repository>
</repositories>
```

Add the Dyconit dependency:

```
<dependency>
  <groupId>science.atlarge.opencraft</groupId>
  <artifactId>dyconits</artifactId>
  <version>1.0.7</version>
</dependency>
```

## Examples

This section gives examples of how to use the Dyconit library.

### Create a New Dyconit System

The following code creates a new Dyconit system with the given policy.
The policy must be provided by the library user and controls which state updates are forwarded to which subscribers,
and controls the consistency bounds for each subscriber.

```java
DyconitPolicy<SubKey, Message> policy = new MyCustomPolicy();
DyconitSystem<SubKey, Message> dcsys = new DyconitSystem<>(policy);
```

### Create a Dyconit / Subscribe to a Dyconit

The following code subscribes `player` to `dyconit-one`, creating the dyconit if necessary.
The `player` is subscribed with consistency bounds of zero, which means every state update affecting `dyconit-one`
is immediately forwarded to the `player`.

```java
dcsys.subscribe(player.getKey(), player.getCallback(), bounds.ZERO, "dyconit-one");
```

### Submit a State Update

The following code submits a state update to the Dyconit system. The configured policy uses the sender to determine
which dyconit is affected by the update. E.g., a state update from a player can affect the dyconit that controls all
state updates in the area in which that player is located.

```java
dcsys.publish(sender, update);
```

### Dynamically Update Consistency Bounds

You can use the `DyconitSubscribeCommand` to update a subscribers consistency bounds on an existing subscription.
This behavior can be automated by including it in your policy's `update` method,
and calling `dcsys.update(player)` periodically (e.g., every game tick).

```java
public class MyCustomPolicy implements DyconitPolicy<SubKey, Message> {
    @Override
    public @NotNull List<DyconitCommand<SubKey, Message>> update(Subscriber<SubKey, Message> player) {
        List<DyconitCommand<SubKey, Message>> commands = new ArrayList<>();
        Bounds bounds = new Bounds(1000, 123);
        commands.add(new DyconitSubscribeCommand<>(player.getKey(), player.getCallback(), bounds, "dyconit-one"));
        return commands;
    }
}
```
