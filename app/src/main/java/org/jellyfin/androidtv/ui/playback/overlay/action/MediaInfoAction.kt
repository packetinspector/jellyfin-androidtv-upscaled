package org.jellyfin.androidtv.ui.playback.overlay.action

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.sdk.model.api.MediaStreamType

class MediaInfoAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	private var dialog: Dialog? = null

	companion object {
		// Semi-transparent HUD colors
		private const val COLOR_BACKGROUND = 0xCC000000.toInt()
		private const val COLOR_SURFACE = 0xFF202124.toInt()
		private const val COLOR_DIVIDER = 0x33FFFFFF.toInt()
		private const val COLOR_LABEL = 0xAAAAAAAAA.toInt()
		private const val COLOR_VALUE = 0xDDFFFFFF.toInt()
		private const val COLOR_HEADER = 0xFFCCCCD0.toInt()
		private const val COLOR_TITLE = 0xEEFFFFFF.toInt()
		private const val COLOR_JELLYFIN_BLUE = 0xFF00A4DC.toInt()
		private const val COLOR_DIRECT_PLAY_BG = 0x441B3A2A.toInt()
		private const val COLOR_DIRECT_PLAY_FG = 0xFF4CAF50.toInt()
		private const val COLOR_TRANSCODE_BG = 0x443A1B1B.toInt()
		private const val COLOR_TRANSCODE_FG = 0xFFEF5350.toInt()
		private const val COLOR_DIRECT_STREAM_BG = 0x441B2A3A.toInt()
		private const val COLOR_DIRECT_STREAM_FG = 0xFF64B5F6.toInt()
	}

	init {
		initializeWithIcon(R.drawable.ic_info)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		videoPlayerAdapter.leanbackOverlayFragment.setFading(false)
		dismissDialog()

		val streamInfo = playbackController.currentStreamInfo
		val mediaSource = streamInfo?.mediaSource

		dialog = Dialog(context).apply {
			requestWindowFeature(Window.FEATURE_NO_TITLE)

			val scrollView = ScrollView(context).apply {
				isVerticalScrollBarEnabled = false
				isFocusable = true
				isFocusableInTouchMode = true
			}

			val container = LinearLayout(context).apply {
				orientation = LinearLayout.VERTICAL
				val hPad = dp(context, 16)
				val vPad = dp(context, 12)
				setPadding(hPad, vPad, hPad, vPad)
			}

			// Title row
			container.addView(TextView(context).apply {
				text = context.getString(R.string.lbl_media_info)
				setTextColor(COLOR_TITLE)
				setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
				typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
				setPadding(0, 0, 0, dp(context, 8))
			})

			// Play method badge
			val playMethod = when {
				streamInfo?.playMethod?.name == "DirectPlay" -> "Direct Play"
				streamInfo?.playMethod?.name == "DirectStream" -> "Direct Stream"
				streamInfo?.playMethod?.name == "Transcode" -> "Transcode"
				else -> "Unknown"
			}

			val (badgeBg, badgeFg) = when (playMethod) {
				"Direct Play" -> COLOR_DIRECT_PLAY_BG to COLOR_DIRECT_PLAY_FG
				"Transcode" -> COLOR_TRANSCODE_BG to COLOR_TRANSCODE_FG
				"Direct Stream" -> COLOR_DIRECT_STREAM_BG to COLOR_DIRECT_STREAM_FG
				else -> COLOR_SURFACE to COLOR_LABEL
			}

			container.addView(TextView(context).apply {
				text = playMethod
				setTextColor(badgeFg)
				setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
				typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
				val hPad = dp(context, 8)
				val vPad = dp(context, 3)
				setPadding(hPad, vPad, hPad, vPad)
				background = GradientDrawable().apply {
					setColor(badgeBg)
					cornerRadius = dp(context, 3).toFloat()
				}
			}, LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = dp(context, 8)
			})

			// Video section
			val videoStream = mediaSource?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
			if (videoStream != null) {
				addDivider(context, container)
				addSectionHeader(context, container, "VIDEO")

				videoStream.codec?.let { addInfoRow(context, container, "Codec", it.uppercase()) }
				if ((videoStream.width ?: 0) > 0 && (videoStream.height ?: 0) > 0) {
					addInfoRow(context, container, "Resolution", "${videoStream.width} \u00D7 ${videoStream.height}")
				}
				videoStream.videoRange?.let { addInfoRow(context, container, "Range", it.toString()) }
				videoStream.realFrameRate?.let { addInfoRow(context, container, "Framerate", String.format("%.2f fps", it)) }
				if ((videoStream.bitRate ?: 0) > 0) {
					addInfoRow(context, container, "Bitrate", formatBitrate(videoStream.bitRate ?: 0))
				}
			}

			// Audio section
			val audioStream = mediaSource?.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }
			if (audioStream != null) {
				addDivider(context, container)
				addSectionHeader(context, container, "AUDIO")

				audioStream.codec?.let { addInfoRow(context, container, "Codec", it.uppercase()) }
				audioStream.language?.let { addInfoRow(context, container, "Language", it) }
				if ((audioStream.channels ?: 0) > 0) {
					addInfoRow(context, container, "Channels", formatChannels(audioStream.channels ?: 0))
				}
				if ((audioStream.sampleRate ?: 0) > 0) {
					addInfoRow(context, container, "Sample Rate", "${audioStream.sampleRate} Hz")
				}
				if ((audioStream.bitRate ?: 0) > 0) {
					addInfoRow(context, container, "Bitrate", formatBitrate(audioStream.bitRate ?: 0))
				}
			}

			// Subtitle section
			val subtitleStream = mediaSource?.mediaStreams?.firstOrNull {
				it.type == MediaStreamType.SUBTITLE && it.isDefault
			}
			if (subtitleStream != null) {
				addDivider(context, container)
				addSectionHeader(context, container, "SUBTITLE")

				subtitleStream.codec?.let { addInfoRow(context, container, "Codec", it.uppercase()) }
				subtitleStream.language?.let { addInfoRow(context, container, "Language", it) }
				if (subtitleStream.isExternal) addInfoRow(context, container, "Source", "External")
			}

			// General section
			addDivider(context, container)
			addSectionHeader(context, container, "GENERAL")

			mediaSource?.container?.let { addInfoRow(context, container, "Container", it.uppercase()) }
			mediaSource?.size?.let { addInfoRow(context, container, "File Size", formatFileSize(it)) }

			scrollView.addView(container)
			setContentView(scrollView)

			window?.apply {
				setBackgroundDrawable(GradientDrawable().apply {
					setColor(COLOR_BACKGROUND)
					cornerRadius = dp(context, 8).toFloat()
				})
				setLayout(dp(context, 280), WindowManager.LayoutParams.WRAP_CONTENT)
				setGravity(Gravity.TOP or Gravity.END)
				val params = attributes
				params.x = dp(context, 24)
				params.y = dp(context, 24)
				attributes = params
				setDimAmount(0.15f)
			}

			setOnDismissListener {
				videoPlayerAdapter.leanbackOverlayFragment.setFading(true)
				this@MediaInfoAction.dialog = null
			}
		}

		dialog?.show()
	}

	private fun dp(context: Context, value: Int): Int {
		return TypedValue.applyDimension(
			TypedValue.COMPLEX_UNIT_DIP,
			value.toFloat(),
			context.resources.displayMetrics
		).toInt()
	}

	private fun addDivider(context: Context, container: LinearLayout) {
		container.addView(View(context).apply {
			setBackgroundColor(COLOR_DIVIDER)
		}, LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			1
		).apply {
			topMargin = dp(context, 2)
			bottomMargin = dp(context, 2)
		})
	}

	private fun addSectionHeader(context: Context, container: LinearLayout, text: String) {
		container.addView(TextView(context).apply {
			this.text = text
			setTextColor(COLOR_JELLYFIN_BLUE)
			setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
			typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
			letterSpacing = 0.1f
			setPadding(0, dp(context, 4), 0, dp(context, 4))
		})
	}

	private fun addInfoRow(context: Context, container: LinearLayout, label: String, value: String) {
		val row = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
			setPadding(0, dp(context, 1), 0, dp(context, 1))
		}

		row.addView(TextView(context).apply {
			text = label
			setTextColor(COLOR_LABEL)
			setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
			typeface = Typeface.create("sans-serif", Typeface.NORMAL)
		}, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

		row.addView(TextView(context).apply {
			text = value
			setTextColor(COLOR_VALUE)
			setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
			typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
			gravity = Gravity.END
		}, LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
		))

		container.addView(row, LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
		))
	}

	fun dismissDialog() {
		dialog?.dismiss()
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
}
