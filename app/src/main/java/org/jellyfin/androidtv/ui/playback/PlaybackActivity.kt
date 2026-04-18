package org.jellyfin.androidtv.ui.playback

import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import coil3.ImageLoader
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.playback.nextup.NextUpFragment
import org.jellyfin.androidtv.ui.playback.pip.PiPManager
import org.jellyfin.androidtv.ui.playback.stillwatching.StillWatchingFragment
import org.jellyfin.androidtv.ui.player.video.VideoPlayerFragment
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.UUID

class PlaybackActivity : FragmentActivity() {
	companion object {
		const val EXTRA_POSITION = "position"
	}

	private val userPreferences by inject<UserPreferences>()
	private val pipManager by inject<PiPManager>()
	private val backgroundService by inject<BackgroundService>()
	private val imageLoader by inject<ImageLoader>()

	var isInPipMode = false
		private set

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_playback)

		// Register callback so MainActivity can finish us when the app exits
		pipManager.finishPlaybackActivity = { finish() }

		// Keep screen on during playback
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

		// Hide system bars for immersive playback
		WindowCompat.setDecorFitsSystemWindows(window, false)
		val insetsController = WindowCompat.getInsetsController(window, window.decorView)
		insetsController.hide(WindowInsetsCompat.Type.systemBars())
		insetsController.systemBarsBehavior =
			WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

		if (savedInstanceState == null) {
			launchPlayerFragment(intent)
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		Timber.i("onNewIntent — replacing player fragment with new queue")
		launchPlayerFragment(intent)
	}

	private fun launchPlayerFragment(intent: Intent) {
		val position = intent.getIntExtra(EXTRA_POSITION, 0)

		val fragment: Fragment = if (userPreferences[UserPreferences.playbackRewriteVideoEnabled]) {
			VideoPlayerFragment().apply {
				arguments = bundleOf(VideoPlayerFragment.EXTRA_POSITION to position)
			}
		} else {
			CustomPlaybackOverlayFragment().apply {
				arguments = bundleOf("Position" to position)
			}
		}

		supportFragmentManager.beginTransaction()
			.replace(R.id.playback_container, fragment)
			.commit()
	}

	/**
	 * Show the NextUp screen within PlaybackActivity.
	 * Called by CustomPlaybackOverlayFragment when a video ends.
	 */
	fun showNextUp(itemId: UUID) {
		Timber.i("Showing NextUp for item $itemId in PlaybackActivity")
		val fragment = NextUpFragment().apply {
			arguments = bundleOf(NextUpFragment.ARGUMENT_ITEM_ID to itemId.toString())
		}
		supportFragmentManager.beginTransaction()
			.replace(R.id.playback_container, fragment)
			.commit()
	}

	/**
	 * Show the StillWatching screen within PlaybackActivity.
	 */
	fun showStillWatching(itemId: UUID) {
		Timber.i("Showing StillWatching for item $itemId in PlaybackActivity")
		val fragment = StillWatchingFragment().apply {
			arguments = bundleOf(StillWatchingFragment.ARGUMENT_ITEM_ID to itemId.toString())
		}
		supportFragmentManager.beginTransaction()
			.replace(R.id.playback_container, fragment)
			.commit()
	}

	/**
	 * Play the next item from the queue. Called by NextUp/StillWatching fragments
	 * when the user confirms (or the timer expires).
	 * Always passes position 0 so the next episode starts from the beginning,
	 * matching the stock behavior of Destinations.videoPlayer(0).
	 */
	fun playNextItem() {
		Timber.i("Playing next item from queue in PlaybackActivity")
		val nextIntent = Intent(intent).apply {
			putExtra(EXTRA_POSITION, 0)
		}
		launchPlayerFragment(nextIntent)
	}

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		// Check if a child fragment wants to consume the back press first
		// (e.g., popup panel or guide in CustomPlaybackOverlayFragment)
		val currentFragment = supportFragmentManager.findFragmentById(R.id.playback_container)
		if (currentFragment is CustomPlaybackOverlayFragment) {
			// Let the fragment's own OnBackPressedCallback handle popup/guide dismissal.
			// If it doesn't consume, we fall through here.
			// The fragment's backPressedCallback is registered via onAttach, so
			// onBackPressedDispatcher will try it first.
		}

		if (pipManager.isPiPEnabled(this)) {
			Timber.i("Back pressed — entering PiP mode")
			val entered = pipManager.enterPiP(this)
			if (entered) return
		}

		@Suppress("DEPRECATION")
		super.onBackPressed()
	}

	override fun onPictureInPictureModeChanged(
		isInPictureInPictureMode: Boolean,
		newConfig: Configuration,
	) {
		super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
		isInPipMode = isInPictureInPictureMode
		pipManager.isCurrentlyInPiP = isInPictureInPictureMode

		Timber.i("PiP mode changed: $isInPictureInPictureMode")

		if (isInPictureInPictureMode) {
			// In PiP mode: free memory to make room for browsing UI
			// Clear backdrop bitmaps — the browsing UI will reload them as needed
			backgroundService.clearBackgrounds()

			// Clear Coil's memory cache to reduce pressure — images will reload from disk cache
			imageLoader.memoryCache?.clear()
			Timber.i("Freed backdrop bitmaps and cleared image cache for PiP mode")
		} else {
			// Exiting PiP — either expanding to fullscreen or being dismissed.
			// If the lifecycle is at CREATED or below, the user dismissed the PiP window
			// (not expanding). In that case, finish the activity so the player stops.
			// onStop already ran (and was skipped) while in PiP, so without this
			// the player keeps running in the background.
			if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
				Timber.i("PiP was dismissed — finishing activity to stop playback")
				finish()
			}
		}
	}

	override fun onTrimMemory(level: Int) {
		super.onTrimMemory(level)

		if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
			Timber.w("Memory trim requested (level=$level) — clearing image caches")
			imageLoader.memoryCache?.clear()
		}
	}

	override fun onUserLeaveHint() {
		super.onUserLeaveHint()

		// Auto-enter PiP when user presses Home
		if (pipManager.isPiPEnabled(this)) {
			Timber.i("onUserLeaveHint — entering PiP mode")
			pipManager.enterPiP(this)
		}
	}

	// Forward key events to fragments (same pattern as MainActivity)
	private fun Fragment.onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
		var result = childFragmentManager.fragments.any { it.onKeyEvent(keyCode, event) }
		if (!result && this is View.OnKeyListener) result = onKey(currentFocus, keyCode, event)
		return result
	}

	private fun onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean =
		supportFragmentManager.fragments.any { it.onKeyEvent(keyCode, event) }

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyDown(keyCode, event)

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)

	override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyLongPress(keyCode, event)

	override fun onStop() {
		super.onStop()

		if (!isInPipMode) {
			Timber.i("PlaybackActivity stopped (not in PiP) — clearing keep screen on")
			window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		}
	}

	override fun onDestroy() {
		// Always clear PiP state on destroy — prevents orphaned player state
		isInPipMode = false
		pipManager.isCurrentlyInPiP = false
		pipManager.finishPlaybackActivity = null

		super.onDestroy()

		Timber.i("PlaybackActivity destroyed — PiP state cleared")

		// Restore system bars
		WindowCompat.setDecorFitsSystemWindows(window, true)
		WindowCompat.getInsetsController(window, window.decorView)
			.show(WindowInsetsCompat.Type.systemBars())

		// Reset display mode
		val params = window.attributes
		params.preferredDisplayModeId = 0
		window.attributes = params
	}
}
