package science.atlarge.opencraft.dyconits

import science.atlarge.opencraft.dyconits.policies.DyconitPolicy
import science.atlarge.opencraft.messaging.Filter
import java.util.*

class DyconitSystem<SubKey, Message>(
    policy: DyconitPolicy<SubKey, Message>,
    val filter: Filter<SubKey, Message>,
    private val messageQueueFactory: MessageQueueFactory<Message> = DefaultQueueFactory(),
) {
    var policy = policy
        set(value) {
            clear()
            field = value
        }
    private val dyconits = HashMap<String, Dyconit<SubKey, Message>>()
    private val subs = HashMap<SubKey, MutableSet<Dyconit<SubKey, Message>>>()
    private val callbackMap = HashMap<SubKey, MessageChannel<Message>>()

    init {
        System.setProperty("kotlinx.coroutines.scheduler", "off")
    }

    fun globalUpdate() {
        policy.globalUpdate().forEach { it.execute(this) }
    }

    fun update(sub: Subscriber<SubKey, Message>) {
        val commands = policy.update(sub)
        commands.forEach { it.execute(this) }
    }

    fun getDyconit(name: String): Dyconit<SubKey, Message> {
        return dyconits.getOrPut(name, { Dyconit(name, messageQueueFactory) })
    }

    fun removeDyconit(name: String): Boolean {
        return dyconits.remove(name) != null
    }

    fun removeDyconit(dyconit: Dyconit<SubKey, Message>): Boolean {
        return removeDyconit(dyconit.name)
    }

    fun subscribe(sub: SubKey, callback: MessageChannel<Message>, bounds: Bounds, name: String) {
        val filteredCallback = callbackMap.getOrPut(sub, {
            object : MessageChannel<Message> {
                override fun send(msg: Message) {
                    if (filter.filter(sub, msg)) {
                        callback.send(msg)
                    }
                }

                override fun flush() {
                    callback.flush()
                }
            }
        })
        subscribe(sub, filteredCallback, bounds, getDyconit(name))
    }

    fun subscribe(sub: SubKey, callback: MessageChannel<Message>, bounds: Bounds, dyconit: Dyconit<SubKey, Message>) {
        dyconit.addSubscription(sub, bounds, callback)
        subs.getOrPut(sub, { HashSet() }).add(dyconit)
    }

    fun unsubscribeAll(sub: SubKey) {
        subs[sub]?.toList()?.forEach { d -> unsubscribe(sub, d) }
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
        dyconit.takeIf { it.countSubscribers() <= 0 }?.let { removeDyconit(it) }
    }

    fun getDyconits(): Collection<Dyconit<SubKey, Message>> {
        return ArrayList(dyconits.values)
    }

    fun getDyconits(subscriber: SubKey): Collection<Dyconit<SubKey, Message>> {
        return subs.getOrDefault(subscriber, HashSet())
    }

    /**
     * Publish the given message. The currently configured policy selects on which dyconit to publish the message,
     * based on the given publisher.
     */
    fun publish(publisher: Any, message: Message) {
        val name = policy.computeAffectedDyconit(publisher)
        dyconits[name]?.let { publish(message, it) }
    }

    /**
     * Publish a message to a dyconit.
     */
    fun publish(message: Message, dyconit: Dyconit<SubKey, Message>) {
        dyconit.addMessage(DMessage(message, policy.weigh(message)))
    }

    /**
     * For each dyconit, synchronize state updates to each subscriber whose consistency bounds have been exceeded.
     */
    fun synchronize(): Error {
        return subs.entries.parallelStream().map {
            var numError = 0
            var staleness = java.time.Duration.ZERO
            for (dyconit in it.value) {
                val subscription = dyconit.getSubscription(it.key) ?: continue
                val error = subscription.synchronize()
                if (!error.exceedsBounds) {
                    numError += error.numerical
                    if (error.staleness > staleness) {
                        staleness = error.staleness
                    }
                }
            }
            callbackMap[it.key]?.flush()
            Error(staleness, numError, true)
        }.reduce(Error.ZERO, Error::plus)
    }

    fun countDyconits(): Int {
        return dyconits.size
    }

    /**
     * Removes all existing dyconits. Currently queued messages are sent to subscribers before the data is cleared.
     */
    fun clear() {
        dyconits.forEach { (_, v) ->
            run {
                v.close()
                v.synchronize()
            }
        }
        dyconits.clear()
        subs.clear()
    }
}
