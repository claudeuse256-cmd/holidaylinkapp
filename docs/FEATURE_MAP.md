# HolidayLink feature map

## Home/base
- Chats is the first screen after login.
- Empty state when there are no conversations.
- Search, notifications, contacts, users and menu are immediately accessible.

## Identity
- Phone number is the primary account identifier.
- SMS OTP is a 6-digit code delivered through the Infobip SMS API (not Infobip 2FA).
- The app has a country-code selector, default Uganda (+256), and always sends a
  normalised international number such as `+256745473752`.
- Email is optional backup information.
- Display names are not unique.
- `@username` is unique, like Telegram.

## People
- Contacts matching is designed around phone hashes, not raw phone-number uploads.
- Users directory lists all platform users.
- Direct chat can be started from Users.

## Messaging
- Direct conversations
- Group conversations
- Group creation
- Message storage
- WebSocket real-time delivery
- Typing events
- Notification records
- Read/delivery schema is present for expansion
- Reply/edit/delete fields are present in the database

## Calls
- Call records and call signaling WebSocket events are present.
- The current Android build has audio/video call buttons and the server signaling foundation.
- Actual microphone/camera WebRTC media is the next implementation layer; it is deliberately separated from auth/chat so it doesn't destabilize the first test build.

## HolidayLink extras
The menu is the home for:
- AI Help
- Find Buddies
- Stories / Status
- Quizzes
- Past Papers
- Events
- Feed
- Videos
- Store
- Calls
- Saved Messages
- Privacy & Security
- Settings
- Profile

The old website ZIP is not copied into the Android app as-is because it depends heavily on Firebase. Its feature pages should be migrated one by one to the new PostgreSQL API rather than carrying Firebase back into the new app.
