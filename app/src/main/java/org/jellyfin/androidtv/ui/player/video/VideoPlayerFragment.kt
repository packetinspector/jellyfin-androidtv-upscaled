package org.jellyfin.androidtv.ui.player.video

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.playback.PlaybackActivity
import org.jellyfin.androidtv.ui.playback.VideoQueueManager
import org.jellyfin.androidtv.ui.playback.pip.PiPManager
import org.jellyfin.androidtv.ui.playback.rewrite.RewriteMediaManager
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.queue.queue
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class VideoPlayerFragment : Fragment() {
	companion object {
		const val EXTRA_POSITION: String = "position"
	}

	private val videoQueueManager by inject<VideoQueueManager>()
	private val playbackManager by inject<PlaybackManager>()
	private val api by inject<ApiClient>()
	private val pipManager by inject<PiPManager>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Create a queue from the items added to the legacy video queue
		val queueSupplier = RewriteMediaManager.BaseItemQueueSupplier(api, videoQueueManager.getCurrentVideoQueue(), false)
		Timber.i("Created a queue with ${queueSupplier.items.size} items")
		playbackManager.queue.clear()
		playbackManager.queue.addSupplier(queueSupplier)

		// Set position
		arguments?.getInt(EXTRA_POSITION)?.milliseconds?.let {
			lifecycleScope.launch {
				playbackManager.state.seek(it)
			}
		}

		// Pause player until the initial resume
		playbackManager.state.pause()

		// Wire video size to PiP manager for correct aspect ratio
		lifecycleScope.launch {
			playbackManager.state.videoSize.collect { size ->
				if (size.width > 0 && size.height > 0) {
					pipManager.updateAspectRatio(size.width, size.height)
				}
			}
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		JellyfinTheme {
			VideoPlayerScreen()
		}
	}

	override fun onPause() {
		super.onPause()

		// Guard: do not pause the player when the activity enters PiP — video should keep playing.
		// But if the activity is finishing (user dismissed PiP or app exiting), always pause.
		val hostActivity = activity
		if (hostActivity is PlaybackActivity && hostActivity.isInPipMode && !hostActivity.isFinishing) return

		playbackManager.state.pause()
	}

	override fun onResume() {
		super.onResume()

		playbackManager.state.unpause()
	}

	override fun onStop() {
		super.onStop()

		// Guard: do not stop the player when the activity enters PiP — but always clean up
		// if the activity is finishing (PiP dismissed, app exiting, etc.)
		val hostActivity = activity
		if (hostActivity is PlaybackActivity && hostActivity.isInPipMode && !hostActivity.isFinishing) {
			Timber.i("Skipping playbackManager.stop() — activity is in PiP mode")
			return
		}

		playbackManager.state.stop()
	}
}
