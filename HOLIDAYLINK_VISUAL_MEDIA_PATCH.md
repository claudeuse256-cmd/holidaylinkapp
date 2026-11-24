# HolidayLink visual/media patch

## Changes in this archive

- Status media editor uses an immersive dark canvas and clearer tool hierarchy.
- Photo editor now offers explicit Original, Square, 4:3, 16:9 and 9:16 output crop presets.
- Crop aspect ratio is passed from the editor through both status publishing and chat attachment editing into `MediaEditor.applyPhotoEdits`.
- The crop is applied to the exported JPEG bitmap; it is not merely a preview zoom.
- Update checks now query the public GitHub Releases API before the Render `/app/latest` feed, avoiding a sleeping backend as the first dependency.
- GitHub API version and user-agent headers are set for the release lookup.

## Important limitations

This is a focused patch, not a claim that every previously requested feature is complete.
- The crop presets are centered aspect-ratio crops with zoom framing; a draggable four-corner/freeform crop rectangle is not implemented in this patch.
- Video trimming and preview code existed in the supplied source. This patch does not certify that every input codec/container uploads successfully.
- Full status notification deep-link behavior, chat-list lifecycle caching, sticker artwork quality, and the complete screenshot-perfect editor still require device-level verification and further work.
- The Android build could not be run here: Gradle 8.9 distribution download failed because `services.gradle.org` could not be resolved in this environment. Run GitHub Actions to compile against the real Android SDK.

## Verification performed

- Confirmed crop ratio callback is wired through both status and chat media editor call sites.
- Confirmed aspect-ratio crop math is present in the image export helper.
- Confirmed GitHub Releases is checked before Render.
- ZIP integrity checked after packaging.
