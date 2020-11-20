package science.atlarge.opencraft.dyconits

sealed class ChannelItem<Message>
class ChannelMessage<Message>(val msg: Message) : ChannelItem<Message>()
class FlushToken<Message> : ChannelItem<Message>()

interface MessageChannel<Message> {
    fun send(msg: Message)
    fun flush()
}
