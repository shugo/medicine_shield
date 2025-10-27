package net.shugo.medicineshield.notification

/**
 * Interface for providing current time
 * This abstraction allows for easier testing by enabling time control in tests
 */
interface TimeProvider {
    /**
     * Get the current time in milliseconds since epoch
     */
    fun currentTimeMillis(): Long
}
