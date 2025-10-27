package net.shugo.medicineshield.notification

/**
 * Default implementation of TimeProvider using System.currentTimeMillis()
 */
class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
