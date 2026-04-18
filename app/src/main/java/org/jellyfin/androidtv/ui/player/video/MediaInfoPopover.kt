package org.jellyfin.androidtv.ui.player.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.popover.Popover
import org.jellyfin.androidtv.ui.browsing.composable.inforow.InfoRowColors
import org.jellyfin.playback.core.mediastream.MediaConversionMethod
import org.jellyfin.playback.core.mediastream.MediaStreamAudioTrack
import org.jellyfin.playback.core.mediastream.MediaStreamVideoTrack
import org.jellyfin.playback.core.mediastream.PlayableMediaStream

@Composable
fun MediaInfoPopover(
	expanded: Boolean,
	onDismissRequest: () -> Unit,
	mediaStream: PlayableMediaStream?,
	fileSize: Long?,
) {
	Popover(
		expanded = expanded,
		onDismissRequest = onDismissRequest,
		alignment = Alignment.TopCenter,
		offset = DpOffset(0.dp, (-10).dp)
	) {
		Column(
			modifier = Modifier
				.widthIn(min = 280.dp)
				.padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			// Title
			Text(
				text = stringResource(R.string.lbl_media_info),
				fontSize = 18.sp,
				fontWeight = FontWeight.W700
			)

			// Play Method Badge
			PlayMethodBadge(mediaStream?.conversionMethod)

			Divider()

			// Video Info Section
			mediaStream?.tracks
				?.filterIsInstance<MediaStreamVideoTrack>()
				?.firstOrNull()
				?.let { videoTrack ->
					VideoInfoSection(videoTrack)
					Divider()
				}

			// Audio Info Section
			mediaStream?.tracks
				?.filterIsInstance<MediaStreamAudioTrack>()
				?.firstOrNull()
				?.let { audioTrack ->
					AudioInfoSection(audioTrack)
					Divider()
				}

			// General Info Section
			GeneralInfoSection(
				container = mediaStream?.container?.format,
				fileSize = fileSize
			)
		}
	}
}

@Composable
private fun Divider() {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(1.dp)
			.background(Color.White.copy(alpha = 0.2f))
	)
}

@Composable
private fun PlayMethodBadge(method: MediaConversionMethod?) {
	val (label, colors) = when (method) {
		MediaConversionMethod.None -> stringResource(R.string.lbl_direct_play) to InfoRowColors.Green
		MediaConversionMethod.Remux -> stringResource(R.string.lbl_direct_stream) to InfoRowColors.Default
		MediaConversionMethod.Transcode -> stringResource(R.string.lbl_transcoding) to InfoRowColors.Red
		null -> "Unknown" to InfoRowColors.Default
	}

	val (backgroundColor, foregroundColor) = colors

	Row(
		modifier = Modifier
			.background(backgroundColor, RoundedCornerShape(6.dp))
			.padding(horizontal = 12.dp, vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			color = foregroundColor,
			fontSize = 16.sp,
			fontWeight = FontWeight.W600
		)
	}
}

@Composable
private fun VideoInfoSection(track: MediaStreamVideoTrack) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		SectionHeader(text = "Video")
		InfoRow(label = "Codec", value = track.codec.uppercase())
		if (track.width > 0 && track.height > 0) {
			InfoRow(label = "Resolution", value = "${track.width} x ${track.height}")
		}
		if (track.bitrate > 0) {
			InfoRow(label = "Bitrate", value = formatBitrate(track.bitrate))
		}
	}
}

@Composable
private fun AudioInfoSection(track: MediaStreamAudioTrack) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		SectionHeader(text = "Audio")
		InfoRow(label = "Codec", value = track.codec.uppercase())
		if (track.channels > 0) {
			InfoRow(label = "Channels", value = formatChannels(track.channels))
		}
		if (track.sampleRate > 0) {
			InfoRow(label = "Sample Rate", value = "${track.sampleRate} Hz")
		}
		if (track.bitrate > 0) {
			InfoRow(label = "Bitrate", value = formatBitrate(track.bitrate))
		}
	}
}

@Composable
private fun GeneralInfoSection(container: String?, fileSize: Long?) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		SectionHeader(text = "General")
		container?.let { format ->
			InfoRow(label = "Container", value = format.uppercase())
		}
		fileSize?.let { size ->
			InfoRow(label = "File Size", value = formatFileSize(size))
		}
	}
}

@Composable
private fun SectionHeader(text: String) {
	Text(
		text = text,
		fontSize = 15.sp,
		fontWeight = FontWeight.W600,
		color = Color.White.copy(alpha = 0.7f)
	)
}

@Composable
private fun InfoRow(label: String, value: String) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			fontSize = 15.sp,
			fontWeight = FontWeight.W400,
			color = Color.White.copy(alpha = 0.8f)
		)
		Spacer(modifier = Modifier.weight(1f))
		Text(
			text = value,
			fontSize = 15.sp,
			fontWeight = FontWeight.W500,
			color = Color.White
		)
	}
}

private fun formatFileSize(bytes: Long): String {
	val kb = 1024.0
	val mb = kb * 1024
	val gb = mb * 1024

	return when {
		bytes >= gb -> String.format("%.2f GB", bytes / gb)
		bytes >= mb -> String.format("%.1f MB", bytes / mb)
		bytes >= kb -> String.format("%.0f KB", bytes / kb)
		else -> "$bytes B"
	}
}

private fun formatBitrate(bitrate: Int): String {
	val kbps = bitrate / 1000.0
	val mbps = kbps / 1000.0

	return when {
		mbps >= 1.0 -> String.format("%.1f Mbps", mbps)
		kbps >= 1.0 -> String.format("%.0f kbps", kbps)
		else -> "$bitrate bps"
	}
}

private fun formatChannels(channels: Int): String {
	return when (channels) {
		1 -> "Mono"
		2 -> "Stereo"
		6 -> "5.1"
		8 -> "7.1"
		else -> "$channels ch"
	}
}
