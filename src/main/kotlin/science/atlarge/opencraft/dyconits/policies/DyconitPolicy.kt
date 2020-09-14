package science.atlarge.opencraft.dyconits.policies

import science.atlarge.opencraft.dyconits.Subscriber

interface DyconitPolicy<SubKey, Message> {
    fun update(sub: Subscriber<SubKey, Message>): List<DyconitCommand<SubKey, Message>>
    fun weigh(message: Message): Int
    fun computeAffectedDyconit(any: Any): String
}
