package science.atlarge.opencraft.dyconits

import com.google.common.collect.Maps
import java.util.function.Consumer

/**
 * A dyconit is similar to a _Topics_ in a Pub/Sub system,
 * and is the core of the bounded-inconsistency communication system in Opencraft.
 *
 * Outgoing messages are sent to a dyconit, which lazily forwards them to subscribers (players)
 * while preventing large inconsistency.
 */
class Dyconit<SubKey, Message>(val name: String) {
    private var subscriptions: MutableMap<SubKey, Subscription<Message>> = Maps.newConcurrentMap()

    fun addSubscription(sub: SubKey, bounds: Bounds, callback: Consumer<Message>) {
        subscriptions.putIfAbsent(sub, Subscription(bounds, callback))
    }

    fun removeSubscription(sub: SubKey) {
        subscriptions.remove(sub)
    }

    fun addMessage(message: DMessage<Message>) {
        subscriptions.entries.parallelStream()
            .map { e -> e.value }
            .forEach { s -> s.addMessage(message) }
    }

    fun countSubscribers(): Int {
        return subscriptions.size
    }

    fun getSubscribers(): List<SubKey> {
        return subscriptions.keys.toList()
    }
}
