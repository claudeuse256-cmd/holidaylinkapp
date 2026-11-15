# HolidayLink messaging UI update

This update removes the custom in-app notification banner. Message and call alerts now use Android system notifications only. In the foreground, the existing Firestore unread listener posts through `Notifier`; the currently open conversation is filtered to avoid notifying the chat being read. In the background/killed state, Firebase Cloud Messaging is handled by `HolidayMessagingService`.

Push delivery still depends on: (1) notification permission being granted on the device, (2) FCM token registration for the signed-in account, (3) the Render backend being deployed with `FIREBASE_SERVICE_ACCOUNT_JSON` configured, and (4) the Firebase project matching `google-services.json`. Android force-stop blocks FCM until the app is manually opened again.

Sticker messages now render their Lottie JSON as looping animations in the conversation instead of treating the JSON URL as an image. The built-in picker uses transparent sticker artwork. The preset chat background uses a subdued, small line-art messaging doodle pattern inspired by the visual language of Telegram/WhatsApp; it is not an official Telegram wallpaper asset or a pixel-identical copy.

Direct chats now use the neutral label `Contact` while the peer profile resolves, rather than exposing a `Loading contact…` placeholder.
