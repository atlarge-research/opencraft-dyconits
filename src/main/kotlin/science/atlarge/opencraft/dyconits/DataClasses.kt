package science.atlarge.opencraft.dyconits

import java.time.Duration

data class Subscriber<SubKey, Message>(val key: SubKey, val callback: MessageChannel<Message>)

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
data class Bounds(val staleness: Int, val numerical: Int) {
    companion object {
        val ZERO = Bounds(0, 0)
        val INFINITE = Bounds(-1, -1)
    }
}

data class Error(val staleness: Duration, val numerical: Int, val exceedsBounds: Boolean) {
    companion object {
        val ZERO = Error(Duration.ZERO, 0, false)
    }

    fun plus(error: Error): Error {
        return Error(staleness + error.staleness, numerical + error.numerical, exceedsBounds || error.exceedsBounds)
    }
}
