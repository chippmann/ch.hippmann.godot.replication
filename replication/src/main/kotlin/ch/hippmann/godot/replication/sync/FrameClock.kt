package ch.hippmann.godot.replication.sync

import godot.api.Time

/** One time read per frame, shared by every replica, so interpolation never pays a JNI call per property. */
object FrameClock {
    var nowMilliseconds: Long = 0
        private set

    var frame: Long = 0
        private set

    fun advance() {
        nowMilliseconds = Time.getTicksMsec()
        frame++
    }
}
