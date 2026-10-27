# Publishing Breathe

The repository is ready for both stores. The steps below are the ones only the owner can do: creating accounts, keys and secrets, and filling in store forms.

Both stores read the listing from `fastlane/metadata/android/en-US/`:

| File | Used for |
|---|---|
| `title.txt` | App name (max 30 characters on Play) |
| `short_description.txt` | Short description (max 80) |
| `full_description.txt` | Full description (max 4000; plain text, which both stores display) |
| `changelogs/<versionCode>.txt` | What's new (max 500) |
| `images/icon.png` | 512 × 512 icon |
| `images/featureGraphic.png` | 1024 × 500 feature graphic (required by Play) |
| `images/phoneScreenshots/` | Phone screenshots (Play needs at least 2) |

## Every release

1. Bump `versionCode` (by 1) and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Commit, merge to `main`, then tag the merge commit and push the tag:
   ```
   git tag v1.1
   git push origin v1.1
   ```

The tag starts the **Release** workflow (`.github/workflows/release.yml`), which builds the signed APK and app bundle, creates a GitHub release with the APK and, once the Play secrets are set, uploads the bundle to Google Play. F-Droid notices the new tag by itself and builds it within a few days.

## F-Droid

F-Droid builds the app from source and signs it with its own key, so nothing has to be uploaded. Release builds are left unsigned unless an upload key is configured, and the dependency metadata block that F-Droid rejects is turned off.

First release:

1. Tag `v1.0` as above (the recipe builds that tag).
2. Create a GitLab account, fork <https://gitlab.com/fdroid/fdroiddata> and copy `fdroid/com.genowhirl.breathe.yml` from this repository to `metadata/com.genowhirl.breathe.yml` in the fork.
3. Optionally check it locally with `fdroid readmeta`, `fdroid lint com.genowhirl.breathe` and `fdroid build -v -l com.genowhirl.breathe` (see the fdroiddata README).
4. Open a merge request using the **App inclusion** template and tick its checklist. The reviewers may ask for changes; once it is merged the app appears in F-Droid after the next index update.

Later releases need nothing beyond the tag: `UpdateCheckMode: Tags` and `AutoUpdateMode: Version` make F-Droid add a build for each new `v*` tag. The F-Droid listing follows the `fastlane/` folder on the tagged commit.

Notes on the recipe:
- It deletes `app/debug.keystore` before building, because the F-Droid scanner rejects binary key files. Only debug builds use it.
- The app has no anti-features: no ads, tracking, network access or non-free dependencies.

## Google Play

### One-time setup

1. **Developer account.** Register at <https://play.google.com/console> (one-time fee, identity verification). A new *personal* account must run a closed test with at least 12 testers for 14 continuous days before it can publish to production; an organisation account does not.
2. **Upload key.** Create it once and keep it (and its passwords) somewhere safe and backed up:
   ```
   keytool -genkeypair -v -keystore upload.jks -alias upload \
       -keyalg RSA -keysize 4096 -validity 10000
   ```
   Google holds the actual app signing key (Play App Signing); this key only proves uploads come from you, and Google can reset it if it is lost.
3. **Local builds** (optional): create `keystore.properties` in the repository root. It is in `.gitignore`; never commit it or the `.jks` file.
   ```
   storeFile=upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
   Then `./gradlew bundleRelease` writes `app/build/outputs/bundle/release/app-release.aab`.
4. **GitHub secrets** (repository Settings → Secrets and variables → Actions):
   - `UPLOAD_KEYSTORE_BASE64`: the output of `base64 -w0 upload.jks`
   - `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`
5. **Create the app** in Play Console: name *Breathe*, app (not game), free, default language English (United States).
6. **First upload by hand.** The Play API can only update an app that already has a build. Push the `v1.0` tag, open the Release workflow run and download the `breathe-bundle` artifact (or build it locally as in step 3). In Play Console, upload `breathe-v1.0.aab` under *Testing → Internal testing → Create new release* and accept Play App Signing.
7. **Automatic uploads** (optional, after step 6): in Google Cloud, create a service account, enable the *Google Play Android Developer API*, create a JSON key, then invite the service account's email in Play Console → *Users and permissions* with permission to release to testing tracks (and production if you want). Save the JSON as the `PLAY_SERVICE_ACCOUNT_JSON` secret. Uploads go to the `internal` track as a draft; set the repository variables `PLAY_TRACK` (e.g. `production`) and `PLAY_RELEASE_STATUS` (`completed`) to change that.

### Store listing

*Grow → Store presence → Main store listing*: copy the title, short and full description from `fastlane/metadata/android/en-US/` and upload the icon, feature graphic and screenshots from its `images/` folder. Category: **Health & Fitness**. Contact email: required. Website: <https://github.com/geno-whirl/Breathe>.

### App content (Policy → App content)

| Form | Answer |
|---|---|
| Privacy policy | `https://github.com/geno-whirl/Breathe/blob/main/PRIVACY.md` |
| Ads | No ads |
| App access | All functionality is available without special access |
| Content rating | Fill in the IARC questionnaire: category *Utility, productivity, communication or other*, answer No to everything. Expect *Everyone* / PEGI 3. |
| Target audience | 13 and over (choosing under 13 brings the Families policy; the app is not designed for children) |
| Data safety | No data collected, no data shared. Data is not encrypted in transit because none is transmitted. Users can delete their data by uninstalling. |
| Health apps | Yes, the app has health features: *Meditation and mindfulness* / *Stress management and relaxation*. It is not a medical device. |
| Government apps, financial features, news | No |
| Foreground service permissions | Declare **Media playback**: "During a breathing session the app plays audio cues (tones or spoken words) for each breathing stage, timed to the user's pattern, while the screen is off and the phone is locked. The user starts the session and can pause or stop it from the notification." Attach a short screen recording of starting a session, locking the phone and the notification controls. |

### Releasing

1. Internal testing: add yourself as a tester and install from the opt-in link.
2. Closed testing (required for new personal accounts): 12+ testers for 14 days.
3. Production: *Promote release*, or run the Release workflow with `PLAY_TRACK=production`. The first review usually takes a few days.
