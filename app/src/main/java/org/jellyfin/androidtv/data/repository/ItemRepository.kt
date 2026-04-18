package org.jellyfin.androidtv.data.repository

import org.jellyfin.sdk.model.api.ItemFields

object ItemRepository {
	/**
	 * Full field set for detail views and playback — includes media sources, chapters, etc.
	 */
	val itemFields = setOf(
		ItemFields.CAN_DELETE,
		ItemFields.CHANNEL_INFO,
		ItemFields.CHAPTERS,
		ItemFields.CHILD_COUNT,
		ItemFields.CUMULATIVE_RUN_TIME_TICKS,
		ItemFields.DATE_CREATED,
		ItemFields.DISPLAY_PREFERENCES_ID,
		ItemFields.GENRES,
		ItemFields.ITEM_COUNTS,
		ItemFields.MEDIA_SOURCE_COUNT,
		ItemFields.MEDIA_SOURCES,
		ItemFields.MEDIA_STREAMS,
		ItemFields.OVERVIEW,
		ItemFields.PATH,
		ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
		ItemFields.TAGLINES,
		ItemFields.TRICKPLAY,
	)

	/**
	 * Lightweight field set for list/grid views (home rows, library grids).
	 * Omits CHAPTERS, MEDIA_SOURCES, MEDIA_STREAMS, OVERVIEW, PATH, TRICKPLAY
	 * which are not displayed on cards and add significant JSON payload.
	 */
	val listItemFields = setOf(
		ItemFields.CAN_DELETE,
		ItemFields.CHANNEL_INFO,
		ItemFields.CHILD_COUNT,
		ItemFields.CUMULATIVE_RUN_TIME_TICKS,
		ItemFields.DATE_CREATED,
		ItemFields.GENRES,
		ItemFields.MEDIA_SOURCE_COUNT,
		ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
		ItemFields.TAGLINES,
	)
}
