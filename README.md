# Tokipin Android

Native Android app for Tokipin: leave a small mark (word, emoji, place, time) on the moments you want to remember. Kotlin + Jetpack Compose, Android 8.0+ (minSdk 26, targetSdk 36), package `com.tokipin.app`, no account or location/calendar permissions. Records stay on the device. Optional usage statistics use Google Analytics for Firebase.

Design source: Figma "Tokipin — Brand · Native App" (pages 01 브랜드, 02 기록 입력, 03 기록 탐색, 04 제품 기준). Copy, policies and editor rules are shared with the homepage in `../tokipin`; behavior and the data format match the iOS app in `../tokipin-ios`.

## Usage statistics

New users first explore the sample calendar. “Start my journal” opens the optional sharing sheet before the personal editor keyboard. Existing or restored users with no previous choice see the same sheet on their normal entry screen. Declining or dismissing keeps collection off. Settings can change the choice at any time. Firebase initializes only after opt-in. Only successful, changed, nonempty manual saves log the parameter-free `record_saved` event. The app never sends entries, their dates or search text. Advertising identifiers, ad personalization and automatic screen reporting are disabled.

For a configured build, download the Android config for `com.tokipin.app` from Firebase project `tokipin-app` and place it at `app/google-services.json` (gitignored). Without it debug builds run locally and omit the sharing sheet and switch. Release builds require a config matching both the project and package.

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
- **First run** opens three months of localized, in-memory examples. Users can browse freely, dismiss the optional cues, or practice saving an emoji. “Start my journal” discards the sample session and opens today’s empty editor. Later launches open 기록한 날. An unfinished draft (also after the process is killed) reopens the editor.
- **Editor**: items stack in entered order. The four tools (단어 · 이모지 · 장소 · 시간) stay above the keyboard; switching tool confirms the current value first. Enter confirms one item (after Hangul composition ends), ✓ saves the whole day including the value being typed. Tap an item to edit it, swipe left to reveal delete, long-press or `…` to multi-select. Deletes can be undone. Emoji tool opens the person's keyboard (a short hint appears only while the field is empty or something invalid was typed) and keeps only valid emoji sequences; time tool shows Material's numeric time input inline, in the device's 12/24-hour format, with `+` for one time and ✓ for the whole day. Save failure keeps the input and offers 다시 시도. Back keeps the draft; `+` continues it.
- **Browse** (four levels, pinch or the top-right view menu; a pinch zooms around the date or month between the fingers, the view follows the fingers slightly, and a pinch released near 1× changes nothing): 일간 (week strip, swipe the strip for weeks and the body for days, item `…` → 수정/삭제, day `…` → delete day), 기록한 날 (bento cards by month, long-press → "이날 기록 N개 삭제" with 8 s undo), 월간 달력 (vertical months, fixed weekday header, up to three entries + "+N"), 연간 (vertical years of mini calendars). `오늘` jumps back to today. Back goes up a level.
- **Search**: words, emoji, places, times, kind names ("장소") and dates ("11.25"), newest first. Opening a result highlights the item; "검색 결과" returns to the list.
- **Settings** (last entry of the view menu): vibration, optional usage statistics, JSON export (system file picker), storage note, privacy policy and terms.
- **Haptics**: one `SEGMENT_TICK` (API 34+, `CLOCK_TICK` before) when the day or level settles; off when the setting is off.
- **Language**: Korean only for now. Every string is in `res/values/strings.xml` and dates use `YYYY.MM.DD(요일)` / cards `DD / 요일` from the app locale, so devices in other languages still get a fully Korean app. To add a language: translate `strings.xml` into `values-<lang>/`, add the code to `AppLocale.supported` and `res/xml/locales_config.xml`.

## Calendar range

- The calendar ends at the current month (records allowed up to its last day) and starts at the earlier of 11 months ago and the oldest saved record (`rules/CalendarRange.kt`, `AppClock` pins the date in tests).
- Month view: before the recent 12 months, 6+ consecutive empty months fold into one row that opens in place; year view: whole past years with no records fold into one row, months outside the range are dimmed and disabled. The selected month focus, the per-day record dots and the year/month summary dots show together.
- The editor's date opens the Material date picker (nothing after this month); new fragments move to the chosen day after its records, a saved day's edits stay as that day's draft.
- Debug: Settings › 개발용 테스트 데이터 › 달력 범위 예시 넣기 loads the shared 512-day fixture (`app/src/debug/assets/calendar-range-records.json`, SHA-256 530bfab8…).

## Daily reading and editing

- **Day view:** an empty day shows the shared empty-state picture (peek rabbit behind a glass card, `ui/EmptyState.kt`: 172×120dp group, 90×90 rabbit, 44% of the page width up to 172dp, centred at 30% of the space between the band and the bottom buttons) and `+`; TalkBack reads "이날 남긴 기록이 없어요". A filled day shows its fragments for reading only and one pencil ("이날 기록 편집") that opens all of them with the keyboard closed.
- **Editor:** tap a fragment to edit it; long press and release in place for a 삭제 menu; long press and drag to reorder across kinds (one tick on pick-up, edge auto-scroll); swipe left still deletes. TalkBack has 위로 이동 / 아래로 이동 / 삭제 actions. Undo brings back only the deleted fragment and keeps later edits. ✓ saves and returns to the same day (empty again after removing the last fragment). Drafts are kept per date.
- **Empty month in 기록한 날:** the same picture inside the month page (it slides with the page); "12월에 남긴 기록이 없어요" is announced once when that month settles.

## Native UX v0.2 (shared with iOS)

The interaction rules come from `Tokipin-Native-UX-v0.2` (md + json with 82 cases: the 59 of v0.1 plus 23 for recorded days), kept once in the web repo at `../tokipin/native-spec/v0.2/`, not copied here. The build pins its SHA-256 (`verifyUxSpec`), unit tests run every case against `rules/UxRules.kt`, and `UxSpecDeviceTest` drives the cases that need a screen with real touches.

- 기록한 날 (v0.2): a fixed year header and a centred month rail over one page per month. The body pages ±1 month with the day view's swipe rules, the rail follows the drag, can be tapped or flung across years and snaps to the nearest month; empty months are included. `RecordedMonths` in `UxRules.kt` is the single place a month is committed (one haptic tick). Device tests run in separate processes through the AndroidX Test Orchestrator.
- All tuning values and product rules are in `rules/UxRules.kt` (`UxSpec`, `SwipeRules`, `PinchRules`, `HapticRules`, `CalendarRules`, `TimeRules`, `EmojiRules`). Platform code (`SwipePager`, `Gestures`, `Haptics`, `TimeDisplay`) only converts Android events.
- Emoji data: `rules/EmojiData.kt` holds Unicode 15.0 Extended_Pictographic / Emoji_Presentation / Emoji_Modifier ranges, generated from the JDK 21 `Character` API so every Android version uses the same table.

```sh
./gradlew :app:testDebugUnitTest                 # 77 JSON cases + rules + sample data (JVM)
ANDROID_SERIAL=<device> ./gradlew :app:connectedDebugAndroidTest   # gestures, calendar state, haptic counts
```

## Debug sample data

Debug builds show **설정 › 개발용 테스트 데이터** with "예시 기록 넣기" (replace all records with three years of samples ending today) and "모든 기록 지우기" (check the empty home). The samples are an exact port of the homepage generator (`../tokipin/trial.js`, `trial-content.js`) in `app/src/debug/.../SampleData.kt`, with the same item ids as iOS. `SampleDataTest` compares every date, kind and value with the web generator through node (`tools/web-samples.js`). Release builds use `app/src/release/.../SampleData.kt`, which has no data and hides the rows.

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
