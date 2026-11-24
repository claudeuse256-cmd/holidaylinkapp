# HolidayLink upgrade review — 15 November 2026

This upgrade starts from `holidaylinkapp-main-fixed.zip` and keeps the Firebase/Render
architecture. It does not pad the archive with filler files to hit a byte target.

## Implemented in this source update

- Real status mention selection from the user's existing one-to-one chat contacts.
- Status mention recipient UIDs and handles are stored separately from free-typed text.
- Publishing a status with selected mentions creates a direct-chat context message and an
  inbox notification for each mentioned account; the normal chat push relay is invoked.
- Mention chips resolve to the selected user's actual UID and open their profile.
- Status replies open the direct chat with the status preloaded as the reply target.
- Status reactions (❤️ 😂 😮 😢 🙏 🔥) are written per-user under the status and also sent as
  a reply-context message in the status owner's chat, with an inbox notification.
- Status photo/video selection now opens the full-screen media editor used by chat attachments.
  Photo edits are applied to a new JPEG copy; video trim ranges are saved and passed into the
  existing MediaExtractor/MediaMuxer trim implementation before upload.
- Status editor toolbar exposes music, crop/zoom, animated sticker, text and draw controls.
- Selected audio is uploaded to Firebase Storage and its URL is saved on the status; viewers
  can play/pause it.
- Statuses store selected animated sticker IDs and render the bundled Lottie animations.
- Chat sticker tiles now prefer bundled Lottie resources over the old animated Unicode fallback.
- Direct-chat creation now merges its metadata instead of overwriting existing chat preview and
  unread fields.
- Added a GitHub Actions Android workflow for Gradle wrapper validation, unit tests, debug APK
  assembly and APK artifact upload.
- Added unit tests for status mention parsing.

## Already present in the reviewed source

- Local-first Firestore cache and persistent media cache.
- Background FCM push relay and Firebase Messaging service.
- Server-backed scheduled-message worker and UI.
- Biometric app lock and per-chat lock.
- Chat message edit, reply, reactions and disappearing-message fields/flows.
- Group admin controls and scheduled message controls.
- Existing 50 bundled Lottie JSON sticker resources.
- JPEG downscaling/compression and photo edit helpers.

## Not completed / must not be advertised as complete

- The status editor's exact final pixel match to the supplied WhatsApp screenshots still needs
  device-side visual QA and refinement. The editor is full-screen and exposes the tools, but it
  is not a pixel-for-pixel clone and does not yet provide full free-position/resize/rotate sticker
  transforms or a full music search catalogue.
- Status reaction aggregates and viewer counts are not yet rendered as a complete interactive
  reactions summary in the management screen.
- Video trim compatibility depends on the input codec/container; unsupported files show an error.
- Animated stickers are Lottie JSON resources. Native Telegram TGS/WebP import/export and a
  server-backed sticker marketplace are not implemented by this change.
- Video messages, in-app message translation, linked-device management, channel discussions,
  polls and all remaining items in the proposed 23-feature list need separate implementation
  and device/server verification. Some group, privacy, scheduled-message and lock features
  already existed, but that does not mean every advanced variation is complete.
- Most importantly, the active Firestore chat UI writes plaintext message bodies. The E2EE
  implementation in `E2ee.kt` belongs to the legacy Render conversations API and is not wired
  into the active Firestore chat path. A compatible Android + website encryption migration is
  required before claiming active-chat E2EE.

## Verification performed here

- `StatusMentionParser.kt` compiled with the installed Kotlin compiler.
- Three parser checks passed (handle extraction/deduplication, invalid mentions, email exclusion).
- `./gradlew --offline testDebugUnitTest assembleDebug` could not start because Gradle 8.9 is
  not installed in this environment and `services.gradle.org` could not be resolved.
- Full Android compilation, Firebase rules deployment, Render deployment, and two-account
  end-to-end testing remain pending GitHub Actions and device testing.
