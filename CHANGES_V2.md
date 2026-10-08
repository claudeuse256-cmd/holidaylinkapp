# HolidayLink App V2 — UI/backend deployment foundation

## Included
- Floating glassmorphism bottom navigation: Chats, Contacts, Videos, Stories, Profile, Menu.
- Glass-style HolidayLink feature center.
- In-app public HolidayLink feature WebView for existing website functionality.
- Dedicated Videos landing UI while the social creator/video backend is migrated.
- Profile avatar in the top bar.
- Admin pages are not exposed in the app navigation.
- Render + Neon deployment blueprint and setup guide.
- Automatic PostgreSQL base schema + feature migration on server startup.
- GitHub Actions can receive `HL_API_BASE_URL` from a repository variable.

## Important
This is the first migration/design pass. The existing native chat/auth/status/settings functionality is preserved. The website's Firebase-backed student features are bridged into the app through the public website until their data/API is migrated to the Render + Neon backend. The social entertainment video posting/feed system requested in the sketch still needs its dedicated PostgreSQL/API implementation; it is not falsely marked complete here.


## V2.1 changes requested before production
- Firebase is retired from the new backend source of truth. Existing Firebase content is not copied automatically; the admin website is the place to re-upload/republish content into Neon after migration.
- Added server-side 2FA password fields and endpoints. A user must first verify the phone by SMS to enable a 2FA password; later sign-ins can use phone + that 2FA password without requesting an SMS each time.
- Added a social video data model and upload endpoint with a hard 100 MB per-video limit, creator metadata, likes/comments counts, views and a user-video route.
- Video/profile media remains file/object storage, not PostgreSQL blobs. The current ZIP uses Render's local upload fallback; before production, connect a persistent object-storage provider so Render restarts cannot delete media.
- Android launcher icon now uses the supplied HolidayLink logo.


## V2.1 call + secure messaging update
- Added device public-key registration and encrypted message envelopes.
- Added direct WebRTC audio/video signaling with public STUN only (no TURN configured).
- Added call states: calling, ringing, accepted, ended, missed/declined plumbing.
- Added server-side delivery acknowledgement and removal of delivered message rows.
- Added persistent avatar bytes in PostgreSQL as a fallback to Render's ephemeral filesystem.

## 2026-10-07 — V2.1.1 authentication/account security update
- Phone-first sign-in remains the only first-page credential: the server checks the number before choosing 2FA password or SMS.
- Added authenticated `/auth/2fa/status` plus visible Security settings to set/change/disable the 2FA password.
- Added local multi-account storage and an Accounts screen with Add account, Switch, and Remove saved account actions.
- Fixed the 2FA login response so a bcrypt password hash is never returned to the Android client.
- E2EE text messages remain encrypted on the server; server cleanup now waits for recipients to open/read the conversation instead of deleting immediately on delivery.
- Google backup is intentionally not included in this release.


## V2.2.0 UI/auth update

- Reworked the signed-in home to the requested glassmorphic blue/cyan/purple design.
- Exact bottom navigation order: Chats, Users, Channels, Settings, Videos, Menu.
- Status is a horizontal row above search, not a bottom navigation item.
- Added glass header actions for new chat, Users, notifications and profile.
- Added combined glass search entry for chats, users and channels.
- Kept the floating + action for starting a chat.
- Kept phone-first authentication: phone check first, then 2FA for enabled accounts or SMS for all other accounts/new numbers.
- Added 2FA management in Settings > Security and account management in Settings > Accounts.
- Google Backup remains intentionally excluded.
- Version bumped to 2.2.0.
