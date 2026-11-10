# MyBoo

A small, self-hostable location-sharing app in the spirit of Life360. Create a circle, share the invite code, and get a push notification when someone **arrives at**, **leaves**, or **starts driving from** a place you care about.

- `android/` — Kotlin + Jetpack Compose app (foreground location service, FCM push)
- `backend/` — Cloudflare Worker + D1 (SQLite) API; does geofence/driving detection and sends pushes via FCM

## How it works

1. The app registers anonymously (name only) and gets a bearer token.
2. Members join a **circle** with a 6-character invite code and define **places** (name, lat/lng, radius).
3. While sharing is on, a foreground service posts a location fix about every 20 s to `POST /v1/location`.
4. The Worker compares each fix with the previous state:
   - **Arrive / leave** — enter at `radius`, leave only past `radius × 1.25` (hysteresis against GPS jitter); fixes with accuracy > 100 m are ignored for transitions.
   - **Started driving** — speed ≥ 4.5 m/s on 2 consecutive fixes; stops when speed < 1 m/s.
5. Other circle members get an FCM push (`"Sam arrived at Home"`, `"Sam started driving"`). The first-ever fix only sets a baseline, so joining doesn't fire fake arrivals.

## Backend setup

```bash
cd backend
npm install
npx wrangler d1 create myboo        # copy database_id into wrangler.jsonc
npm run db:remote                   # apply schema.sql
npx wrangler secret put FCM_SERVICE_ACCOUNT   # paste Firebase service-account JSON
npm run deploy
```

Local dev: `npm run db:local && npm run dev` (serves on :8787; push is skipped and logged when no FCM secret is set). Tests: `npm test`.

## Android setup

1. Create a Firebase project, add an Android app with package `com.myboo.app`, and put its `google-services.json` in `android/app/` (gitignored).
2. In Firebase → Project settings → Service accounts, generate a key; that JSON is the `FCM_SERVICE_ACCOUNT` secret above.
3. Point the app at your Worker: set `myboo.apiUrl=https://myboo-api.<you>.workers.dev` in `~/.gradle/gradle.properties` (default `http://10.0.2.2:8787` hits `wrangler dev` from the emulator).
4. Open `android/` in Android Studio, or `./gradlew :app:installDebug`.

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/v1/users` | `{name}` → `{id, token}` (no auth) |
| PUT | `/v1/me/fcm-token` | `{token}` |
| GET | `/v1/me/circles` | |
| POST | `/v1/circles` | `{name}` |
| POST | `/v1/circles/join` | `{code}` |
| GET | `/v1/circles/:id` | members (last location, driving, current places) + places |
| POST / DELETE | `/v1/circles/:id/places[/:pid]` | `{name, lat, lng, radius_m}` |
| POST | `/v1/location` | `{lat, lng, speed?, accuracy?, ts?}` |

All but `POST /v1/users` need `Authorization: Bearer <token>`.

## Known limitations / roadmap

- Auth is a bare bearer token per device (no recovery if the app data is cleared). Add accounts before real use.
- Only the latest location is stored — no history/trips.
- Boot auto-resume of sharing may be blocked by Android 14+ background-start rules for location foreground services; open the app once after reboot.
- No map view yet (tapping a person opens their position in your maps app). Maps SDK is the obvious next step.
- No per-person notification preferences or quiet hours.
