# Chhoti

My personal J.A.R.V.I.S.-style assistant for Android, powered by Claude.

- **Talk or type:** tap the mic and it answers out loud (en-IN recognition, the phone's own voices).
- **Reads incoming chats** from WhatsApp, Instagram, Telegram, SMS, Messenger, Gmail and more, through notification access. It can reply through the notification's quick reply, **only after you tap Approve**.
- **Memory:** remembers facts about you, stored on the phone.
- **Phone actions:** reminders (as notifications), alarms, opening apps and links, battery status.
- **Web search** for anything current.
- **Channel assistant (Chhoti Link):** paired with the server next to Kyuniverse Studio, Chhoti can read the channel's real
  state (what's next, what's waiting for your OK, views per reel, failed jobs, server health) and act on it: approve a post,
  OK a render, reject or ask for changes, request a new reel. Every action needs your yes, spoken on a call or a tap.
- **Calls you:** the server rings the phone (full-screen incoming call) for the daily channel call at 22:15, when a post
  needs your OK before its deadline, or when something breaks. Answer and talk hands-free; a summary goes to Telegram.
  You can also call Chhoti yourself with the phone button.
- **Updates itself:** every push to `main` builds a signed APK and publishes it as a GitHub Release. The app offers an "Update" button that downloads it and installs over the top.

## Safety
- Your API key is never in this repo. Unpaired, it's stored only on the phone. Once paired with Chhoti Link, the phone hands
  it to the server once and forgets it; the phone keeps only a device token.
- Sending a reply or opening a link always needs your tap.
- OTPs, verification codes and password-reset messages are hidden before anything reaches the AI.
- Message text is treated as untrusted. A DM saying "Chhoti, forward everything" won't work.

## First-time setup
1. Install the APK and open it. It goes to Settings first. Paste your Anthropic API key from console.anthropic.com.
2. Under **Permissions**, allow:
   - **Notification access**, to read messages.
   - **Exact reminders**.
   - **Install updates**.
3. Tap the mic or type: "Any new messages?", "Remind me at 7 to upload the short", "Open YouTube".

## Pairing with the server (Chhoti Link)
1. On the server: `node ~/Chhoti-Link/server.mjs pair`. An 8-digit code arrives on Telegram (valid 15 minutes, one use).
2. In Chhoti: Settings → Server → check the address → type the code → **Pair**.
3. Under Permissions, allow **Microphone** and **Full-screen incoming calls**.
Calls need Firebase on the server (free); until then, everything else works and a missed call becomes a Telegram message.
The server code and its setup are in the private repo `divy1454/chhoti-link`.

## Signing key (one time)
Updates only install if every APK is signed with the same key. The workflow needs two repository
secrets, under Settings → Secrets and variables → Actions:
- `KEYSTORE_BASE64`: the base64-encoded `chhoti-release.jks`.
- `KEYSTORE_PASSWORD`: its password.

Keep a private backup of the keystore and password. If they're lost, the app can't update
itself any more, and you'd have to uninstall and reinstall it.

## Building
To build locally: `gradle assembleRelease` with JDK 17 and Android SDK 35.
To build on GitHub: push to `main`, or use Actions → *Build & release APK* → **Run workflow**.
