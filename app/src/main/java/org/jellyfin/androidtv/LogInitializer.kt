package org.jellyfin.androidtv

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import timber.log.Timber

/**
 * Minimal Timber tree that only logs warnings and errors.
 * Skips verbose/debug/info to reduce overhead on low-power devices.
 */
private class ReleaseTree : Timber.Tree() {
	override fun isLoggable(tag: String?, priority: Int): Boolean =
		priority >= Log.WARN

	override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
		Log.println(priority, tag ?: "Jellyfin", message)
		if (t != null) Log.println(priority, tag ?: "Jellyfin", Log.getStackTraceString(t))
	}
}

class LogInitializer : Initializer<Unit> {
	override fun create(context: Context) {
		// Use a release tree that only logs warnings and errors
		Timber.plant(ReleaseTree())
	}

	override fun dependencies() = emptyList<Class<out Initializer<*>>>()
}
