package org.jellyfin.androidtv.ui.player.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.base.popover.Popover
import org.jellyfin.androidtv.ui.composable.rememberQueueEntry
import org.jellyfin.androidtv.ui.player.base.PlayerSeekbar
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.mediastream.mediaStreamFlow
import org.jellyfin.playback.core.model.PlayState
import org.jellyfin.playback.core.queue.queue
import org.jellyfin.playback.jellyfin.queue.baseItem
import org.koin.compose.koinInject

@Composable
fun VideoPlayerControls(
	playbackManager: PlaybackManager = koinInject()
) {
	val playState by playbackManager.state.playState.collectAsState()

	Column(
		verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
	) {
		Row(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			modifier = Modifier
				.focusRestorer()
				.focusGroup()
		) {
			PlayPauseButton(playbackManager, playState)
			RewindButton(playbackManager)
			FastForwardButton(playbackManager)
			MediaInfoButton(playbackManager)

			Spacer(Modifier.weight(1f))

			MoreOptionsButton {
				PreviousEntryButton(playbackManager)
				NextEntryButton(playbackManager)
			}
		}

		PlayerSeekbar(
			playbackManager = playbackManager,
			modifier = Modifier
				.fillMaxWidth()
				.height(4.dp)
		)
	}
}

@Composable
private fun PlayPauseButton(
	playbackManager: PlaybackManager,
	playState: PlayState,
) {
	val focusRequester = remember { FocusRequester() }
	IconButton(
		onClick = {
			when (playState) {
				PlayState.STOPPED,
				PlayState.ERROR -> playbackManager.state.play()

				PlayState.PLAYING -> playbackManager.state.pause()
				PlayState.PAUSED -> playbackManager.state.unpause()
			}
		},
		modifier = Modifier
			.focusRequester(focusRequester)
			.onVisibilityChanged {
				focusRequester.requestFocus()
			}
	) {
		AnimatedContent(playState) { playState ->
			when (playState) {
				PlayState.PLAYING -> {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_pause),
						contentDescription = stringResource(R.string.lbl_pause),
					)
				}

				PlayState.STOPPED,
				PlayState.PAUSED,
				PlayState.ERROR -> {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_play),
						contentDescription = stringResource(R.string.lbl_play),
					)
				}
			}
		}
	}
}

@Composable
private fun RewindButton(
	playbackManager: PlaybackManager,
) = IconButton(
	onClick = { playbackManager.state.rewind() },
) {
	Icon(
		imageVector = ImageVector.vectorResource(R.drawable.ic_rewind),
		contentDescription = stringResource(R.string.rewind),
	)
}

@Composable
private fun FastForwardButton(
	playbackManager: PlaybackManager,
) = IconButton(
	onClick = { playbackManager.state.fastForward() },
) {
	Icon(
		imageVector = ImageVector.vectorResource(R.drawable.ic_fast_forward),
		contentDescription = stringResource(R.string.fast_forward),
	)
}

@Composable
private fun MediaInfoButton(
	playbackManager: PlaybackManager,
) {
	val entry by rememberQueueEntry(playbackManager)
	val mediaStream by entry?.mediaStreamFlow?.collectAsState(initial = null)
		?: remember { mutableStateOf(null) }
	val baseItem = entry?.baseItem
	val fileSize = baseItem?.mediaSources?.firstOrNull()?.size

	var expanded by remember { mutableStateOf(false) }

	Box {
		IconButton(
			onClick = { expanded = true },
		) {
			Icon(
				imageVector = ImageVector.vectorResource(R.drawable.ic_info),
				contentDescription = stringResource(R.string.lbl_media_info),
			)
		}

		MediaInfoPopover(
			expanded = expanded,
			onDismissRequest = { expanded = false },
			mediaStream = mediaStream,
			fileSize = fileSize,
		)
	}
}

@Composable
private fun PreviousEntryButton(
	playbackManager: PlaybackManager,
) {
	val entryIndex by playbackManager.queue.entryIndex.collectAsState()
	val coroutineScope = rememberCoroutineScope()

	IconButton(
		enabled = entryIndex > 0,
		onClick = {
			coroutineScope.launch {
				playbackManager.queue.previous()
			}
		},
	) {
		Icon(
			imageVector = ImageVector.vectorResource(R.drawable.ic_previous),
			contentDescription = stringResource(R.string.lbl_prev_item),
		)
	}
}

@Composable
private fun NextEntryButton(
	playbackManager: PlaybackManager,
) {
	val entryIndex by playbackManager.queue.entryIndex.collectAsState()
	val coroutineScope = rememberCoroutineScope()

	IconButton(
		enabled = entryIndex < playbackManager.queue.estimatedSize - 1,
		onClick = {
			coroutineScope.launch {
				playbackManager.queue.next()
			}
		},
	) {
		Icon(
			imageVector = ImageVector.vectorResource(R.drawable.ic_next),
			contentDescription = stringResource(R.string.lbl_next_item),
		)
	}
}

@Composable
private fun MoreOptionsButton(
	content: @Composable () -> Unit,
) = Box {
	var expanded by remember { mutableStateOf(false) }
	IconButton(
		onClick = { expanded = true },
	) {
		Icon(
			imageVector = ImageVector.vectorResource(R.drawable.ic_more),
			contentDescription = stringResource(R.string.lbl_other_options),
		)
	}

	Popover(
		expanded = expanded,
		onDismissRequest = { expanded = false },
		alignment = Alignment.TopCenter,
		offset = DpOffset(0.dp, (-5).dp)
	) {
		Row(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			modifier = Modifier
				.padding(4.dp)
		) {
			content()
		}
	}
}
