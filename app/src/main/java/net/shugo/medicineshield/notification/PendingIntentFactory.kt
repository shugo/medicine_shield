package net.shugo.medicineshield.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Interface for creating PendingIntent instances
 * This abstraction allows for easier testing by enabling mock implementations
 */
interface PendingIntentFactory {
    /**
     * Create a PendingIntent for a broadcast
     */
    fun createBroadcast(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int
    ): PendingIntent
}

/**
 * Default implementation of PendingIntentFactory using Android's PendingIntent
 */
class PendingIntentFactoryImpl : PendingIntentFactory {
    override fun createBroadcast(
        context: Context,
        requestCode: Int,
        intent: Intent,
        flags: Int
    ): PendingIntent {
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
