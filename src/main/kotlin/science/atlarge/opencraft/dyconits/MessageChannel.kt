package science.atlarge.opencraft.dyconits

interface MessageChannel<in Message> {
    fun send(msg: Message)
    fun flush()
}
