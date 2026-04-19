# Changelog

## v1.0.1

### Branding
- App is now branded as **Jellyfin Upscaled** in the Android launcher instead of "Jellyfin Debug"
- Application ID suffix changed from `.debug` to `.upscaled` (package `org.jellyfin.androidtv.upscaled`)
- Still installs side-by-side with the official Jellyfin Android TV client

### Upstream Sync
- Merged upstream `release-0.19.z` branch — includes latest upstream stability fixes and maintenance updates

## v1.0.0

### Picture-in-Picture Support
- Press Back during video playback to shrink the video into a PiP window
- Browse your library and select new content while video continues playing
- Selecting a new video automatically stops PiP and launches fullscreen playback
- Home button also triggers PiP mode
- PiP can be enabled/disabled in user preferences
- Supports non-live-TV video content

### Seek & Skip Improvements
- Integrated upstream seek/skip fix (PR #5462) with seek-in-progress tracking and 8-second timeout
- DPAD left/right now skip backward/forward when the transport overlay is hidden
- Consecutive skips work reliably without pausing or interrupting playback
- Seek override support: new seek commands override in-progress seeks instead of queueing

### Media Info Dialog
- Redesigned media info overlay to match the Jellyfin TV theme
- Color-coded play method badge: green for Direct Play, blue for Direct Stream, red for Transcode
- Displays video range, framerate, subtitle track, and language information
- Clean section layout with labeled headers for Video, Audio, Subtitle, and General info

### Performance Optimizations
- Reduced API payload size for browsing views by using a lighter field set (excludes chapters, media sources, trickplay data, and other heavy fields not needed for list/grid display)
- Coil image loading: 15% memory cache cap with 50MB disk cache for smoother scrolling and reduced memory pressure
- Logging overhead removed: production-level logging (warnings and above only)
- R8 code shrinking enabled in debug builds for release-equivalent runtime performance
- Memory management during PiP: backdrop bitmaps and image caches are cleared when entering PiP to free resources for browsing

### Debug Build
- Debug build uses `.debug` application ID suffix for side-by-side installation with release
- R8 minification enabled for release-like performance while retaining debug signing
