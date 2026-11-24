# Mise: handoff for AI agents

Android **watch journal** for films and shows: a watchlist, a diary of every watch with private voice/text
thoughts, state of mind, a verdict tier and optional per-axis scores, and an Instagram story card that announces
the watch without revealing the review. Kotlin + Compose, package `dev.savar.mise`, minSdk 29 · targetSdk 36 ·
compileSdk 37. Owner: Savar Verma (GitHub `savar95x`), daily driver **OnePlus 13s, OxygenOS 16**, IST. Personal use.
Sibling projects with the same conventions: `savar95x/regista` (template for this repo), `savar95x/hiatus`,
`savar95x/minutia`, `savar95x/aria-player`.

## Golden rules
1. **Never change the signing key (`app/release.jks`, password `mise1234`) or `applicationId`.** Installed copies couldn't update.
2. **Every shipped change = version bump (`version.properties`) + `CHANGELOG.md` entry in the same push.**
   CI publishes `Mise X.Y.Z` with `Mise-X.Y.Z.apk` when the tag doesn't exist yet; otherwise it only build-checks.
3. **Commit as** `Savar Verma <102670483+savar95x@users.noreply.github.com>`.
4. **Reviews are private.** Only the optional "line for your story" ever appears on the card. Sync is text only;
   audio never leaves the phone except in the user's own backup zip.
5. **No single rating.** The owner finds one number too lossy: verdict tiers + optional separate axes, by design.
6. **Material 3 (Expressive), minimal copy, same look as the siblings** (Group rows, Page large top bar, pure black).
7. **Don't claim it works on a phone.** You can't run it. Ask for screenshots.

## Working loop (no Android SDK locally, gh may be unauthenticated)
GitHub Actions is the compiler. Work on a branch (e.g. `dev`): `screenshots.yml` runs all unit tests + renders
screens and force-pushes PNGs, `gradle.log` and test XML to the orphan branch `shots`; `scripts/shots.sh` waits
for it via git and prints compile errors / failing tests. Merge to `main` to release; if the release build fails,
`build.yml` pushes its log to branch `ci-log`. Toolchain copied from Regista: AGP 9.5.1, Compose compiler plugin
2.4.21, BOM 2026.09.00, material3 pinned to 1.5.0-alpha27 for the Expressive APIs.

## Architecture (`app/src/main/java/dev/savar/mise`)
| File | What it does |
|---|---|
| `Model.kt` | `Title` (id `movie:<tmdb>` / `tv:<tmdb>` / `custom:<uuid>`), `Watch` (one sitting; rewatches are new watches), `Memo`, `Tier`, `Axis`, `Setup`, JSON. |
| `Store.kt` | All state as Compose state in `files/journal.json`; tombstones (`deleted`) so deletes sync. `merge()` = last write wins per item, memos unioned. Settings (TMDB token, speech language) in prefs. |
| `Tmdb.kt` | Search (multi) and details; accepts the v4 read token (Bearer) or the v3 key. |
| `Voice.kt` | `Recorder` (AudioRecord 16 kHz → AAC .m4a via MediaCodec/MediaMuxer, PCM tee), `Transcriber` (SpeechRecognizer with `EXTRA_AUDIO_SOURCE` pipe + segmented session, on-device first; Android 13+), `Pcm.decode` (transcribe a saved memo), `Player`. Untested on hardware beyond CI: the recognizer path is the least certain piece and fails soft (memo still saved). |
| `Sync.kt` | Supabase: email/password auth (GoTrue REST), one RLS table `mise_items` (setup SQL in `SETUP_SQL`), pull by server `synced_at`, push since last push, trigger keeps newer `updated_at`. |
| `Backup.kt` | Zip export/import of journal.json + memos (merge on import). |
| `Story.kt`, `StorySheet.kt` | 1080×1920 story card drawn on a platform Canvas in the family palette; Instagram ADD_TO_STORY intent with share-sheet fallback; save to Pictures/Mise. |
| `DiaryScreen.kt`, `WatchlistScreen.kt`, `TitleScreen.kt`, `LogScreen.kt`, `Search.kt`, `SettingsScreen.kt`, `SyncScreen.kt` | Screens. `MainActivity.kt` holds tabs + a small route stack (`Nav`, `LocalNav`). |
| `AppIcons.kt` | Switchable launcher icons (activity-alias per icon, default Clapper). Drawables from `docs/icon/make.py --write` (family palette, ~80 px on OxygenOS, optically centred); never hand-edit them. |
| `Theme.kt`, `Fonts.kt`, `Components.kt`, `Updater.kt`, `Posters.kt`, `Fmt.kt`, `Http.kt` | Shared with the siblings: theme, Google Fonts, Group/NavRow/Page, GitHub Releases updater; poster cache. |
