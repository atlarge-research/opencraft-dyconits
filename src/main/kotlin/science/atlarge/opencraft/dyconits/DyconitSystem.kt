package science.atlarge.opencraft.dyconits

import science.atlarge.opencraft.dyconits.policies.DyconitPolicy
import science.atlarge.opencraft.messaging.Filter
import java.util.function.Consumer

class DyconitSystem<SubKey, Message>(
    var policy: DyconitPolicy<SubKey, Message>,
    val filter: Filter<SubKey, Message>
) {
    private val dyconits = HashMap<String, Dyconit<SubKey, Message>>()
    private val subs = HashMap<SubKey, MutableSet<Dyconit<SubKey, Message>>>()

    fun update(sub: Subscriber<SubKey, Message>) {
        val commands = policy.update(sub)
        commands.forEach { c -> c.execute(this) }
    }

    fun getDyconit(name: String): Dyconit<SubKey, Message> {
        return dyconits.getOrPut(name, { Dyconit(name) })
    }

    fun removeDyconit(name: String): Boolean {
        return dyconits.remove(name) != null
    }

    fun removeDyconit(dyconit: Dyconit<SubKey, Message>): Boolean {
        return removeDyconit(dyconit.name)
    }

    fun subscribe(sub: SubKey, callback: Consumer<Message>, bounds: Bounds, name: String) {
        class Filtered : Consumer<Message> {
            override fun accept(t: Message) {
                if (filter.filter(sub, t)) {
                    callback.accept(t)
                }
            }
        }
        subscribe(sub, Filtered(), bounds, dyconits.getOrPut(name, { Dyconit(name) }))
    }

    fun subscribe(sub: SubKey, callback: Consumer<Message>, bounds: Bounds, dyconit: Dyconit<SubKey, Message>) {
        dyconit.addSubscription(sub, bounds, callback)
        subs.getOrPut(sub, { HashSet() }).add(dyconit)
    }

    fun unsubscribe(sub: SubKey, dyconitName: String) {
        val dyconit = dyconits[dyconitName]
        if (dyconit != null) {
            unsubscribe(sub, dyconit)
        }
    }

    fun unsubscribe(sub: SubKey, dyconit: Dyconit<SubKey, Message>) {
        dyconit.removeSubscription(sub)
        subs.getOrPut(sub, { HashSet() }).remove(dyconit)
    }

    fun getDyconits(): Collection<Dyconit<SubKey, Message>> {
        return ArrayList(dyconits.values)
    }

    fun getDyconits(subscriber: SubKey): Collection<Dyconit<SubKey, Message>> {
        return subs.getOrDefault(subscriber, HashSet())
    }

    fun publish(publisher: Any, message: Message) {
        val name = policy.computeAffectedDyconit(publisher)
        val dyconit = getDyconit(name)
        publish(message, dyconit)
    }

    fun publish(message: Message, dyconit: Dyconit<SubKey, Message>) {
        dyconit.addMessage(DMessage(message, policy.weigh(message)))
    }

    fun countDyconits(): Int {
        return dyconits.size
    }
}
