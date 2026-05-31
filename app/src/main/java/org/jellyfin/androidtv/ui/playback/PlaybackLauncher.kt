package org.jellyfin.androidtv.ui.playback

import android.content.Context
import android.content.Intent
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.pip.PiPManager
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Utility class to launch the playback UI for an item.
 */
class PlaybackLauncher(
	private val mediaManager: MediaManager,
	private val videoQueueManager: VideoQueueManager,
	private val navigationRepository: NavigationRepository,
	private val userPreferences: UserPreferences,
	private val pipManager: PiPManager,
) {
	private val BaseItemDto.supportsExternalPlayer
		get() = when (type) {
			BaseItemKind.MOVIE,
			BaseItemKind.EPISODE,
			BaseItemKind.VIDEO,
			BaseItemKind.SERIES,
			BaseItemKind.SEASON,
			BaseItemKind.RECORDING,
			BaseItemKind.TV_CHANNEL,
			BaseItemKind.PROGRAM,
				-> true

			else -> false
		}

	@JvmOverloads
	fun launch(
		context: Context,
		items: List<BaseItemDto>,
		position: Int? = null,
		replace: Boolean = false,
		itemsPosition: Int = 0,
		shuffle: Boolean = false,
	) {
		val isAudio = items.any { it.mediaType == MediaType.AUDIO }

		if (isAudio) {
			mediaManager.playNow(context, items, itemsPosition, shuffle)
			navigationRepository.navigate(Destinations.nowPlaying)
		} else {
			val items = if (shuffle) items.shuffled() else items

			videoQueueManager.setCurrentVideoQueue(items.toList())
			videoQueueManager.setCurrentMediaPosition(itemsPosition)

			if (items.isEmpty()) return

			val isLiveTv = items.any { it.type == BaseItemKind.TV_CHANNEL || it.type == BaseItemKind.PROGRAM }

			if (userPreferences[UserPreferences.useExternalPlayer] && items.all { it.supportsExternalPlayer }) {
				context.startActivity(ActivityDestinations.externalPlayer(context, position?.milliseconds ?: Duration.ZERO))
			} else if (!isLiveTv && pipManager.isPiPEnabled(context)) {
				// If there's already a PiP'd PlaybackActivity, we have to fully tear it
				// down before launching the new one:
				//   - PiP windows live in their own task stack on TV, so singleTop on
				//     startActivity from MainActivity's task doesn't find them →
				//     a fresh PlaybackActivity is created and the old PiP keeps playing
				//     (orphan PiP window symptom from v1.0.6)
				//   - Calling finish() then immediately startActivity races onDestroy
				//     against new-activity setup → crashes / stuck players / audio
				//     focus thrash (symptom from v1.0.5)
				// stopPiPPlaybackThen finishes the existing activity and defers the
				// startActivity until its onDestroy + fragment teardown have run.
				//
				// Use applicationContext + NEW_TASK because the calling Activity
				// (MainActivity) may have been clobbered by StartupActivity's
				// singleTask clear if the user went home then back via the launcher
				// icon — the deferred lambda may otherwise hold a dead Activity.
				val appContext = context.applicationContext
				val launchIntent = ActivityDestinations.playbackActivity(appContext, position ?: 0).apply {
					addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				}
				pipManager.stopPiPPlaybackThen { appContext.startActivity(launchIntent) }
			} else if (userPreferences[UserPreferences.playbackRewriteVideoEnabled]) {
				val destination = Destinations.videoPlayerNew(position)
				navigationRepository.navigate(destination, replace)
			} else {
				val destination = Destinations.videoPlayer(position)
				navigationRepository.navigate(destination, replace)
			}
		}
	}
}
