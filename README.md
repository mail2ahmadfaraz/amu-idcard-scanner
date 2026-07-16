# AMU ID Card Scanner

Android app that scans the QR code printed on AMU student ID cards and opens the
official verification page — and refuses to do anything with a QR code that
doesn't point to a genuine AMU verification link.

## How the verification works

1. The camera (CameraX + on-device ML Kit) reads the raw text of the QR code.
   No network call is made just to decode the QR.
2. [`QrUrlValidator`](app/src/main/java/in/ac/amuonline/idverifier/QrUrlValidator.kt)
   parses that text as a URL — not a string match — and checks:
   - scheme is exactly `https`
   - the URL has no embedded credentials (`user@host` tricks)
   - the **host** exactly equals one of the allow-listed hosts in
     [`AllowedSources`](app/src/main/java/in/ac/amuonline/idverifier/AllowedSources.kt)
     (the path is not restricted — the host is the actual trust boundary, and
     path patterns turned out to vary/drift in practice)
3. Only if all of that passes does the app open
   [`VerifyResultActivity`](app/src/main/java/in/ac/amuonline/idverifier/VerifyResultActivity.kt),
   which loads the URL in a WebView. The WebView re-validates the URL again on
   entry, and blocks every subsequent navigation/redirect that isn't on the
   same allow-listed host — so a compromised or malicious page can never
   bounce the user off to somewhere else.
4. Anything else (a typo'd look-alike domain, `http://`, a `javascript:` URI,
   a link to an arbitrary site, random text) is rejected with a clear on-screen
   message, and the camera keeps scanning.

Currently allow-listed, based on how this repo's Laravel app generates ID-card
QR codes ([app/Console/Commands/GenerateIdCardAssets.php](../application/app/Console/Commands/GenerateIdCardAssets.php),
[app/Jobs/GenerateIdCardZipJob.php](../application/app/Jobs/GenerateIdCardZipJob.php)):

| Host | Source |
|---|---|
| `moeps.amucoe.ac.in` | external MOEPS verifiable-link service (e.g. `/qr-code/verify/{uuid}`) |
| `nep.amuonline.ac.in` | this app's own fallback verify route (e.g. `/admin/idcards/verify/{id}`) |

If the ID-card generator ever starts issuing links from a new host, add it to
`AllowedSources.ALL` — that's the only place the trust boundary lives.

## Branding

- App name: **AMU ID Card Scanner**
- App icon: generated from the official AMU logo already used by the ID-card
  system, at `application/public/images/amu-logo.png`, composited onto both
  legacy and Android-8+ adaptive icon layers for all densities.

## Android version support

`compileSdk`/`targetSdk` = 35 (Android 15), `minSdk` = 24. Apps that target 35
get full OS compatibility handling on newer releases, so this runs correctly
across Android 14, 15 and 16 (today's last three) — and on essentially every
real device further back too. Bump `compileSdk`/`targetSdk` in
`app/build.gradle.kts` later if you want to specifically target a newer SDK
once it's installed in your toolchain.

## Building the APK

This sandbox has no Android SDK, so the APK itself couldn't be compiled here —
only the project. Two ways to get an installable `.apk`:

### Option A — GitHub Actions (no local install needed)

1. Push this folder to a new GitHub repo:
   ```bash
   cd idcard-verifier-app
   git init
   git add .
   git commit -m "AMU ID Card Scanner"
   git branch -M main
   git remote add origin <your-repo-url>
   git push -u origin main
   ```
2. GitHub Actions (`.github/workflows/build-apk.yml`) builds automatically on
   push. Open the **Actions** tab → the latest run → download the
   `amu-id-card-scanner-debug-apk` artifact.
3. Transfer the `.apk` to an Android phone and install it (you'll need to
   allow "install unknown apps" for whichever app you use to open the file).

This produces a **debug-signed** APK — fine for internal distribution to your
verification staff. If you need a Play Store release build, you'll want to
add a proper release signing key (ask and I'll wire that up).

### Option B — Android Studio, locally

Open the `idcard-verifier-app` folder in Android Studio (Giraffe or newer),
let it sync, then **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## Trying it out

Any of the existing QR codes on already-issued AMU ID cards should scan and
open cleanly. To confirm the rejection path, generate a QR code for any other
URL (e.g. `https://example.com`) and scan it — the app should show a red
"not an official AMU verification service" banner and keep scanning instead
of navigating anywhere.
