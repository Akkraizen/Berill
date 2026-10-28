# Google Calendar

The planner has a **Calendar** page: a month grid, and an hour-by-hour view of any day with events,
timed tasks, all-day events, and deadlines from other sections. Events can be kept only in Berill or
synced both ways with Google Calendar. For the user, connecting Google is a single
**Sign in with Google** button.

## Contents

- [How it works](#how-it-works)
- [One-time setup for builds](#one-time-setup-for-builds)
- [Connecting](#connecting)
- [Sync rules](#sync-rules)
- [Troubleshooting](#troubleshooting)
- [Related documents](#related-documents)

## How it works

```text
Berill device ──OAuth (browser, PKCE)──► accounts.google.com
      │
      └──HTTPS──► Google Calendar API (www.googleapis.com/calendar/v3)
      │
      └──Berill sync──► other devices (events are shared like any other planner data)
```

| Part | Where it lives | Purpose |
| --- | --- | --- |
| OAuth client | Built into the app at compile time | Identifies Berill to Google. Google does not treat the secret of a desktop client as confidential. |
| Refresh token | Secret storage of each device | Lets the device call the API. Every device signs in on its own; tokens never leave the device. |
| Events | Collection `planner_events` | Shared through Berill sync. Events created in Berill keep the same id in Google, so devices do not create duplicates. |

Sign-in opens the system browser and receives the answer on a temporary `http://127.0.0.1:<port>`
address inside the app. The same flow works on Windows, macOS, Linux, and Android.

## One-time setup for builds

Done once by whoever builds the releases. Without it the build still works, but the sign-in button is
hidden.

### Google Cloud

1. Open [console.cloud.google.com](https://console.cloud.google.com) and create a project, for example
   `Berill`.
2. **APIs & Services → Library**: find **Google Calendar API** and press **Enable**.
3. **Google Auth Platform → Branding**: set the app name (`Berill`), the support email, and the
   developer contact.
4. **Google Auth Platform → Data Access → Add or remove scopes**: add
   - `https://www.googleapis.com/auth/calendar.events`
   - `https://www.googleapis.com/auth/calendar.calendarlist.readonly`
5. **Google Auth Platform → Audience**: user type **External**, then **Publish app** to switch the
   status to **In production**. In the *Testing* status Google expires refresh tokens after 7 days.
6. **Google Auth Platform → Clients → Create client**: application type **Desktop app**, name
   `Berill`. Copy the **Client ID** and **Client secret**.

A desktop client is used on every platform, including Android, because sign-in goes through the
loopback address. No Android client or SHA-1 fingerprints are needed.

Calendar scopes are *sensitive*. Until Google verifies the app, users see a "Google hasn't verified
this app" screen and Google allows up to 100 users. For a personal app this is enough; verification
needs a website, a privacy policy, and a short video of the sign-in, and is requested on the
**Verification Center** page.

### Passing the client to the build

| Where | How |
| --- | --- |
| GitHub releases | **Settings → Secrets and variables → Actions**: add `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`. The release workflows pass them to Gradle. |
| Local builds | Add `berill.googleClientId=…` and `berill.googleClientSecret=…` to `~/.gradle/gradle.properties`, or set `BERILL_GOOGLE_CLIENT_ID` and `BERILL_GOOGLE_CLIENT_SECRET`. |

Do not put the values into the repository's `gradle.properties`. The planner module generates
`GoogleClient.kt` into its `build` folder from these values.

## Connecting

1. Open **Planner → Calendar** or **Planner → Settings → Google Calendar** and press
   **Sign in with Google**.
2. Choose the account in the browser and allow access. While the app is unverified, choose
   **Advanced → Go to Berill** on the warning screen.
3. Return to the app. It loads the calendar list and syncs.
4. In settings, choose which calendars to show and which calendar new events go to.

Sign in on every device that should talk to Google. Devices without a Google sign-in still show the
events, because the events arrive through Berill sync.

## Sync rules

| Situation | Result |
| --- | --- |
| Event created in Berill | Created in the chosen calendar. *Berill only* keeps it local. |
| Event created, changed, or deleted in Google | Picked up on the next sync. |
| Event changed in both places before a sync | Google's version wins. |
| Event deleted in Berill | Deleted in Google too. |
| Recurring event in Google | Every occurrence is a separate event in Berill; editing one changes only that occurrence. |
| Calendar unchecked in settings | Its events disappear from Berill; nothing changes in Google. |
| Read-only calendar (holidays, shared) | Shown, but new events cannot be created there. |

Sync runs when the calendar page opens, every 5 minutes while it is open, a moment after each change,
and from the sync button. Berill keeps events from 90 days ago to about a year ahead.

**Disconnect** in settings revokes the device's token. Events already in Berill stay.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| No sign-in button, "unavailable in this build" | The build was made without the OAuth client. Add the secrets or Gradle properties and rebuild. |
| The browser shows `Error 400: redirect_uri_mismatch` | The client type is not **Desktop app**. Create a desktop client and update the secrets. |
| The browser shows `Error 403: access_denied` | The app is still in *Testing* and the account is not a test user. Publish the app. |
| The device asks to sign in again after a week | The app is still in *Testing*. Publish it and sign in again. |
| `Google Calendar API has not been used in project` | Enable the Google Calendar API in the project of this client. |
| The browser page does not return to the app | Finish within 5 minutes and keep the app running while signing in, then press the button again. |

## Related documents

- [README](../README.md) — features, project structure, development setup.
- [RELEASES.md](RELEASES.md) — release workflow and its GitHub secrets.
- [MCP.md](MCP.md) — access to Berill data from AI assistants.
