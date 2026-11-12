# Cloudinary setup (videos, PDFs and video statuses)

The app uploads **videos, documents (including PDFs) and video statuses** to Cloudinary
through the HolidayLink server. Photos keep using imgbb, exactly like the website.

The Cloudinary secret is **never** stored in the APK. The phone sends the bytes to

```
POST https://holidaylink-server.onrender.com/media/upload
Authorization: Bearer <Firebase ID token>
X-Filename: <file name>
X-Content-Type: <mime type>
```

and the server signs the Cloudinary request and returns `{ secureUrl }`.

## 1. Create the Cloudinary credentials

In the Cloudinary console, open **Dashboard** and copy:

- **Cloud name**
- **API Key**
- **API Secret**

(Alternatively create an **unsigned upload preset** under Settings → Upload and use
`CLOUDINARY_UPLOAD_PRESET` instead of the key/secret pair.)

## 2. Add them to Render

Render → **holidaylink-otp** → Environment:

| Key | Value |
| --- | --- |
| `CLOUDINARY_CLOUD_NAME` | your cloud name |
| `CLOUDINARY_API_KEY` | your API key |
| `CLOUDINARY_API_SECRET` | your API secret |
| `CLOUDINARY_FOLDER` | `holidaylink` (optional) |
| `CLOUDINARY_UPLOAD_PRESET` | only if you use an unsigned preset |

Save, then **Manual Deploy → Deploy latest commit** (or wait for auto-deploy).

## 3. Verify

`GET /health` should now include:

```json
"cloudinaryConfigured": true
```

Until the keys are set, `POST /media/upload` answers
`503 CLOUDINARY_NOT_CONFIGURED`, and the app automatically falls back to Firebase
Storage so sending videos and files keeps working in the meantime.

## Limits

- App-side cap: **20 MB** per chat attachment, **30 MB** per video status.
- Server-side cap: **31 MB** per request (`express.raw` limit is 32 MB).
- Cloudinary's own plan limits still apply.
