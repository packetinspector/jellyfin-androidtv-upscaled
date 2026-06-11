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
	private val playbackControllerContainer by inject<PlaybackControllerContainer>()

	var isInPipMode = false
		private set

	/**
	 * Set when onNewIntent arrives while in PiP: Android is about to expand us back
	 * to fullscreen as part of intent delivery. Suppresses the "PiP was dismissed →
	 * finish" heuristic in onPictureInPictureModeChanged, which could otherwise
	 * misfire if the mode-change callback lands before the lifecycle reaches STARTED
	 * during the expand transition.
	 */
	private var pipExitViaIntent = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_playback)

		// Teardown callback for MainActivity's app-exit cleanup. This activity is
		// singleInstance — always alone in its own task — so finishAndRemoveTask
		// is always safe and also removes the task record (a plain finish() can
		// leave a ghost task that absorbs the next launch intent).
		pipManager.finishPlaybackActivity = { finishAndRemoveTask() }

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
		Timber.i("onNewIntent — replacing player fragment with new queue (inPiP=$isInPipMode)")

		// Android will expand the PiP window to fullscreen as part of delivering
		// this intent — that's an expected PiP exit, not a user dismissal.
		if (isInPipMode) pipExitViaIntent = true

		launchPlayerFragment(intent)
	}

	private fun launchPlayerFragment(intent: Intent) {
		// Deterministically stop the current video and release its ExoPlayer
		// BEFORE creating the new player fragment. The fragment lifecycle alone
		// can't be trusted for this: CustomPlaybackOverlayFragment.onStop skips
		// endPlayback while in PiP (by design — PiP keeps playing), so a fragment
		// swap during/right after PiP would leak the old player, which then fights
		// the new one for audio focus and decoders (stutter, overlapping audio).
		// endPlayback is idempotent — no-op if playback already ended.
		playbackControllerContainer.playbackController?.endPlayback()

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

		// commitAllowingStateLoss: this can run from onNewIntent while the activity
		// is PiP'd/paused, possibly after state save. Losing fragment state is fine
		// here — we are wholesale replacing the player with a new queue.
		supportFragmentManager.beginTransaction()
			.replace(R.id.playback_container, fragment)
			.commitAllowingStateLoss()
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
			// Exiting PiP — three possible causes:
			// 1. Intent-driven expand (new video picked while PiP'd): pipExitViaIntent
			//    is set; the activity is coming to the foreground, do nothing.
			// 2. User expanded the PiP window: lifecycle reaches STARTED, do nothing.
			// 3. User dismissed the PiP window (X): lifecycle stays below STARTED —
			//    finish so the player actually stops instead of running headless.
			if (pipExitViaIntent) {
				Timber.i("PiP exited due to incoming play intent — not a dismissal")
				pipExitViaIntent = false
			} else if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
				Timber.i("PiP was dismissed — finishing activity to stop playback")
				finishAndRemoveTask()
			}
		}
	}

	override fun onResume() {
		super.onResume()
		// Any pending intent-driven PiP exit has fully completed once we're resumed.
		pipExitViaIntent = false
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
		// Clear non-fragment-related PiP state.
		isInPipMode = false

		// Safety net: release the ExoPlayer if it's still alive. The fragment's
		// onStop skips endPlayback while in PiP (by design), and the PiP-dismiss →
		// finishAndRemoveTask path can reach onDestroy without a cleanup-eligible
		// onStop. Ownership check: only end playback if the controller's fragment
		// belongs to THIS activity (or has already been detached) — never kill a
		// controller that a newer activity instance has since created.
		val controller = playbackControllerContainer.playbackController
		val controllerActivity = controller?.fragment?.activity
		if (controller != null && (controllerActivity == null || controllerActivity == this)) {
			controller.endPlayback()
		}

		super.onDestroy()

		pipManager.notifyActivityDestroyed()

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
