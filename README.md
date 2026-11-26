# Tokipin Android

Native Android app for Tokipin: leave a small mark (word, emoji, place, time) on the moments you want to remember. Kotlin + Jetpack Compose, Android 8.0+ (minSdk 26, targetSdk 36), package `com.tokipin.app`, no account, no network, no permissions.

Design source: Figma "Tokipin — Brand · Native App" (pages 01 브랜드, 02 기록 입력, 03 기록 탐색, 04 제품 기준). Copy, policies and editor rules are shared with the homepage in `../tokipin`; behavior and the data format match the iOS app in `../tokipin-ios`.

## Run

Open the folder in Android Studio and run `app`, or from a shell (uses Android Studio's bundled JDK 21):

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

With an emulator or device connected, `./gradlew :app:connectedDebugAndroidTest` runs real two-finger pinches through all four levels (`PinchTest`).

## What's in v0.1

- **Start**: the Android system splash shows the peek-rabbit icon. A slow read shows a bento skeleton; a read failure shows "기록을 불러오지 못했어요" with 다시 시도 and never overwrites saved records.
- **First run** opens the editor for today with the word tool; later launches open 기록한 날. An unfinished draft (also after the process is killed) reopens the editor.
- **Editor**: items stack in entered order. The four tools (단어 · 이모지 · 장소 · 시간) stay above the keyboard; switching tool confirms the current value first. Enter confirms one item (after Hangul composition ends), ✓ saves the whole day including the value being typed. Tap an item to edit it, swipe left to reveal delete, long-press or `…` to multi-select. Deletes can be undone. Emoji tool opens the person's keyboard (a short hint appears only while the field is empty or something invalid was typed) and keeps only valid emoji sequences; time tool shows Material's numeric time input inline, in the device's 12/24-hour format, with `+` for one time and ✓ for the whole day. Save failure keeps the input and offers 다시 시도. Back keeps the draft; `+` continues it.
- **Browse** (four levels, pinch or the top-right view menu; a pinch zooms around the date or month between the fingers, the view follows the fingers slightly, and a pinch released near 1× changes nothing): 일간 (week strip, swipe the strip for weeks and the body for days, item `…` → 수정/삭제, day `…` → delete day), 기록한 날 (bento cards by month, long-press → "이날 기록 N개 삭제" with 8 s undo), 월간 달력 (vertical months, fixed weekday header, up to three entries + "+N"), 연간 (vertical years of mini calendars). `오늘` jumps back to today. Back goes up a level.
- **Search**: words, emoji, places, times, kind names ("장소") and dates ("11.25"), newest first. Opening a result highlights the item; "검색 결과" returns to the list.
- **Settings** (last entry of the view menu): touch feedback, JSON export (system file picker), storage note, privacy policy and terms.
- **Haptics**: one `SEGMENT_TICK` (API 34+, `CLOCK_TICK` before) when the day or level settles; off when the setting is off.
- **Language**: Korean only for now. Every string is in `res/values/strings.xml` and dates use `YYYY.MM.DD(요일)` / cards `DD / 요일` from the app locale, so devices in other languages still get a fully Korean app. To add a language: translate `strings.xml` into `values-<lang>/`, add the code to `AppLocale.supported` and `res/xml/locales_config.xml`.

## Native UX v0.1 (shared with iOS)

The interaction rules come from `Tokipin-Native-UX-v0.1` (md + json with 59 cases), kept once in the web repo at `../tokipin/native-spec/v0.1/`, not copied here. The build pins its SHA-256 (`verifyUxSpec`), unit tests run every case against `rules/UxRules.kt`, and `UxSpecDeviceTest` drives the cases that need a screen with real touches.

- All tuning values and product rules are in `rules/UxRules.kt` (`UxSpec`, `SwipeRules`, `PinchRules`, `HapticRules`, `CalendarRules`, `TimeRules`, `EmojiRules`). Platform code (`SwipePager`, `Gestures`, `Haptics`, `TimeDisplay`) only converts Android events.
- Emoji data: `rules/EmojiData.kt` holds Unicode 15.0 Extended_Pictographic / Emoji_Presentation / Emoji_Modifier ranges, generated from the JDK 21 `Character` API so every Android version uses the same table.

```sh
./gradlew :app:testDebugUnitTest                 # 55 JSON cases + rules tests (JVM)
ANDROID_SERIAL=<device> ./gradlew :app:connectedDebugAndroidTest   # gestures, calendar state, haptic counts
```

## Data format

Stored at `files/tokipin/records.json` (atomic writes) with the draft in `files/tokipin/draft.json`. Export writes the same JSON, identical to iOS so files are interchangeable:

```json
{
  "version": 1,
  "days": [
    {
      "date": "2026-11-25",
      "updatedAt": "2026-11-25T10:00:00Z",
      "items": [
        { "id": "UUID", "kind": "word", "value": "살구잼" },
        { "id": "UUID", "kind": "time", "value": "16:20" }
      ]
    }
  ]
}
```

Records are included in Android Auto Backup and device transfer (`res/xml/data_extraction_rules.xml`).

## Brand

Palette 세이지 차콜: Canvas `#39453D`, Surface `#252C29`, Primary `#F3F3EC`, Secondary `#C3CDC5`, Focus `#BBD2F2`. DM Sans (variable TTF, SIL OFL, `licenses/DMSans-OFL.txt`) for numbers and Latin; Hangul falls back to the system Noto Sans CJK instead of bundling the 10 MB Noto Sans KR. Icons are vector drawables traced from the homepage SVGs; the launcher icon is the homepage's 1024 px artwork.

## Guesses to confirm

- Adaptive icon: the 1024 artwork inset 9% on `#4A6461`; the monochrome (themed) icon uses the peek-rabbit outline. Figma's "Android 표시 영역과 안전원" spec was not exported.
- Background gradient, card sizes, view-menu icons and the pinch thresholds (1.25× / 0.8×) were taken by eye from Figma screenshots.
- No release signing is configured yet.
