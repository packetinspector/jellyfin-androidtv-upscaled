package org.jellyfin.androidtv.ui.playback.pip

import android.app.Activity
import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Rational
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.preference.UserPreferences
import timber.log.Timber

class PiPManager(
	private val userPreferences: UserPreferences,
) {
	private val videoAspectRatio = MutableStateFlow(Rational(16, 9))

	/**
	 * Whether a PlaybackActivity is currently in PiP mode.
	 * Updated by PlaybackActivity.onPictureInPictureModeChanged().
	 */
	var isCurrentlyInPiP = false
		internal set

	/**
	 * Callback to finish the PlaybackActivity. Set by PlaybackActivity on create,
	 * cleared on destroy. Used by MainActivity to kill PiP when the app exits.
	 */
	var finishPlaybackActivity: (() -> Unit)? = null

	/**
	 * Request that any active PiP playback stops and finishes.
	 */
	fun stopPiPPlayback() {
		if (isCurrentlyInPiP) {
			Timber.i("Stopping PiP playback via callback")
			finishPlaybackActivity?.invoke()
		}
	}

	fun isPiPSupported(context: Context): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

		return try {
			val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
			appOps.checkOpNoThrow(
				AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
				Process.myUid(),
				context.packageName,
			) == AppOpsManager.MODE_ALLOWED
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Timber.w(e, "Failed to check PiP support via AppOps")
			false
		}
	}

	fun isPiPEnabled(context: Context): Boolean =
		userPreferences[UserPreferences.pipEnabled] && isPiPSupported(context)

	fun updateAspectRatio(width: Int, height: Int) {
		if (width <= 0 || height <= 0) return

		val (clampedW, clampedH) = clampAspectRatio(width, height)
		videoAspectRatio.value = Rational(clampedW, clampedH)
	}

	companion object {
		private const val MAX_RATIO = 2.39f
		private const val MIN_RATIO = 1f / 2.39f

		/**
		 * Clamp an aspect ratio to the range allowed by Android PiP (approximately 1:2.39 to 2.39:1).
		 * Returns the (width, height) pair to use.
		 */
		fun clampAspectRatio(width: Int, height: Int): Pair<Int, Int> {
			if (width <= 0 || height <= 0) return width to height

			val ratio = width.toFloat() / height.toFloat()
			return when {
				ratio > MAX_RATIO -> 239 to 100
				ratio < MIN_RATIO -> 100 to 239
				else -> width to height
			}
		}
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private fun buildPiPParams(): PictureInPictureParams =
		PictureInPictureParams.Builder()
			.setAspectRatio(videoAspectRatio.value)
			.build()

	@RequiresApi(Build.VERSION_CODES.O)
	fun enterPiP(activity: Activity): Boolean {
		if (!isPiPEnabled(activity)) return false

		return try {
			activity.enterPictureInPictureMode(buildPiPParams())
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Timber.e(e, "Failed to enter PiP mode")
			false
		}
	}
}
