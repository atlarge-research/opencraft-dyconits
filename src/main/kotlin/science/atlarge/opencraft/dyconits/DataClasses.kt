package science.atlarge.opencraft.dyconits

import java.time.Instant
import java.util.function.Consumer

data class Subscriber<SubKey, Message>(val key: SubKey, val callback: Consumer<Message>)

/**
 * A Minecraft Message wrapped to include message weight.
 * This weight is used by the dyconit system to compute numerical error.
 */
data class DMessage<Message>(val message: Message, val weight: Int)

/**
 * Consistency bounds.
 *
 * Staleness is measured in milliseconds.
 */
data class Bounds constructor(val staleness: Int, val numerical: Int) {
    var timestampLastReset: Instant = Instant.now()

    companion object {
        val ZERO = Bounds(0, 0)
        val INFINITE = Bounds(-1, -1)
    }
}

data class Counters(
    var dyconitsCreated: Int = 0,
    var dyconitsRemoved: Int = 0,
    var messagesQueued: Int = 0,
    var messagesSent: Int = 0,
    var numericalErrorQueued: Int = 0,
    var numericalErrorSent: Int = 0,
    var stalenessQueued: Long = 0,
    var stalenessSent: Long = 0,
    var numericalErrorBounds: List<Int> = ArrayList(),
    var stalenessBounds: List<Long> = ArrayList(),
)
