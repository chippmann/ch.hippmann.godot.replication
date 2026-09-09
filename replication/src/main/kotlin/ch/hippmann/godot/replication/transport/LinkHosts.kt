package ch.hippmann.godot.replication.transport

/**
 * Hosts that live exactly as long as one link: the outbound host of a dial, and the punched host a member opens for a
 * caller it expects. A punched host nobody dialed dies after [waitingLifetimeMilliseconds].
 */
internal class LinkHosts(private val waitingLifetimeMilliseconds: Long, private val clock: () -> Long = System::currentTimeMillis) {
    private val byLink = HashMap<Long, EnetHost>()
    private val waiting = LinkedHashMap<EnetHost, Long>()

    val all: Collection<EnetHost>
        get() = byLink.values + waiting.keys

    fun expectCaller(host: EnetHost) {
        waiting[host] = clock() + waitingLifetimeMilliseconds
    }

    fun attach(linkKey: Long, host: EnetHost) {
        waiting.remove(host)
        byLink[linkKey] = host
    }

    fun release(linkKey: Long) {
        byLink.remove(linkKey)?.destroy()
    }

    /** Services every host; a waiting host gets [handlerFor] it so the transport learns which host a new link came from. */
    fun service(handler: EnetEventHandler, handlerFor: (EnetHost) -> EnetEventHandler) {
        for (host in byLink.values.toList()) host.service(handler)
        for (host in waiting.keys.toList()) host.service(handlerFor(host))
        expireWaiting()
    }

    private fun expireWaiting() {
        val now = clock()
        val expired = waiting.filterValues { deadline -> deadline < now }.keys
        for (host in expired) {
            waiting.remove(host)
            host.destroy()
            TransportLog.log { "nobody dialed the punched host at port ${host.port}" }
        }
    }

    fun flush(): Unit = all.forEach(EnetHost::flush)

    fun destroyAll() {
        all.forEach(EnetHost::destroy)
        byLink.clear()
        waiting.clear()
    }
}
