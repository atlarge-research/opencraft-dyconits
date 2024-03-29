package science.atlarge.opencraft.dyconits.policies

import science.atlarge.opencraft.dyconits.Bounds
import science.atlarge.opencraft.dyconits.DyconitSystem
import science.atlarge.opencraft.dyconits.MessageChannel

interface DyconitCommand<SubKey, Message> {
    fun execute(dyconitSystem: DyconitSystem<SubKey, Message>)
}

class DyconitSubscribeCommand<SubKey, Message>(
    val subscriber: SubKey,
    val callback: MessageChannel<Message>,
    val bounds: Bounds,
    val name: String
) :
    DyconitCommand<SubKey, Message> {
    override fun execute(dyconitSystem: DyconitSystem<SubKey, Message>) {
        dyconitSystem.subscribe(subscriber, callback, bounds, name)
    }
}

class DyconitUnsubscribeCommand<SubKey, Message>(val subscriber: SubKey, val dyconitName: String) :
    DyconitCommand<SubKey, Message> {
    override fun execute(dyconitSystem: DyconitSystem<SubKey, Message>) {
        dyconitSystem.unsubscribe(subscriber, dyconitName)
    }
}

class DyconitCreateCommand<SubKey, Message>(val dyconitName: String) : DyconitCommand<SubKey, Message> {
    override fun execute(dyconitSystem: DyconitSystem<SubKey, Message>) {
        dyconitSystem.getDyconit(dyconitName)
    }
}

data class DyconitRemoveCommand<SubKey, Message>(val dyconitName: String) : DyconitCommand<SubKey, Message> {
    override fun execute(dyconitSystem: DyconitSystem<SubKey, Message>) {
        dyconitSystem.removeDyconit(dyconitName)
    }
}

class DyconitChangePolicyCommand<SubKey, Message>(val dcPolicy: DyconitPolicy<SubKey, Message>) :
    DyconitCommand<SubKey, Message> {
    override fun execute(dyconitSystem: DyconitSystem<SubKey, Message>) {
        dyconitSystem.policy = dcPolicy
    }
}

class DyconitClearCommand<SubKey, Message>() : DyconitCommand<SubKey, Message> {
    override fun execute(dyconitSystem: DyconitSystem<SubKey, Message>) {
        dyconitSystem.clear()
    }
}
