package com.tokipin.app.data

import java.time.LocalDate

/**
 * Debug builds only: the homepage's three-year sample journal (../tokipin trial.js and
 * trial-content.js), ported exactly so the web demo, iOS and Android show the same days and the
 * same fragments for any "today". Release builds use the stub in src/release, which has no data.
 */
object SampleData {
    const val AVAILABLE = true

    private data class Theme(val words: List<String>, val places: List<String>, val emoji: List<String>, val from: Int, val to: Int)

    private val words = mapOf(
        "walk" to "산책",
        "dawn" to "새벽",
        "birdsong" to "새소리",
        "breeze" to "산들바람",
        "leaves" to "나뭇잎",
        "shade" to "그늘",
        "tea" to "차 한 잔",
        "teapot" to "찻주전자",
        "steam" to "김",
        "coffee" to "커피",
        "firstSip" to "첫 모금",
        "cup" to "머그잔",
        "cycling" to "자전거 타기",
        "pedals" to "페달",
        "swim" to "수영",
        "ripples" to "물결",
        "goggles" to "물안경",
        "melody" to "멜로디",
        "headphones" to "헤드폰",
        "favoriteSong" to "좋아하는 노래",
        "reading" to "독서",
        "bookmark" to "책갈피",
        "lastPage" to "마지막 장",
        "tomatoes" to "토마토",
        "basket" to "바구니",
        "herbs" to "허브",
        "cooking" to "요리",
        "soup" to "수프",
        "simmer" to "보글보글",
        "sunset" to "노을",
        "clouds" to "구름",
        "pinkSky" to "분홍빛 하늘",
        "umbrella" to "우산",
        "puddles" to "물웅덩이",
        "raindrops" to "빗방울",
        "sketch" to "스케치",
        "pencil" to "연필",
        "portrait" to "초상화",
        "bloom" to "첫 꽃",
        "watering" to "물주기",
        "sprout" to "새싹",
        "dough" to "반죽",
        "loaf" to "갓 구운 빵",
        "crust" to "빵 껍질",
        "blanket" to "담요",
        "picnic" to "피크닉",
        "grapes" to "포도",
        "waves" to "파도",
        "sand" to "모래",
        "shells" to "조개껍데기",
        "stones" to "조약돌",
        "reflection" to "물에 비친 하늘",
        "train" to "기차",
        "windowSeat" to "창가 자리",
        "ticket" to "표 한 장",
        "stars" to "별빛",
        "moon" to "달",
        "nightSky" to "밤하늘",
        "camera" to "카메라",
        "shadow" to "그림자",
        "photo" to "사진 한 장",
        "pottery" to "도예",
        "clay" to "찰흙",
        "bowl" to "작은 그릇",
        "puzzle" to "퍼즐",
        "lastPiece" to "마지막 조각",
        "corners" to "모서리 조각",
        "letter" to "편지",
        "envelope" to "봉투",
        "handwriting" to "손글씨",
        "climbing" to "오르막길",
        "view" to "탁 트인 풍경",
        "footsteps" to "발걸음",
        "stretching" to "스트레칭",
        "breathing" to "심호흡",
        "balance" to "균형",
        "painting" to "그림",
        "sculpture" to "조각상",
        "blue" to "파랑",
        "candle" to "촛불",
        "quiet" to "고요함",
        "journal" to "일기",
        "home" to "집",
        "park" to "공원",
        "garden" to "정원",
        "trail" to "산책로",
        "riverside" to "강변",
        "lake" to "호숫가",
        "pool" to "수영장",
        "kitchen" to "부엌",
        "cafe" to "카페",
        "library" to "도서관",
        "bookshop" to "서점",
        "market" to "시장",
        "balcony" to "발코니",
        "window" to "창가",
        "street" to "골목",
        "studio" to "작업실",
        "bakery" to "빵집",
        "beach" to "해변",
        "bridge" to "다리 위",
        "station" to "기차역",
        "hill" to "언덕",
        "table" to "식탁",
        "desk" to "책상",
        "hilltop" to "언덕 꼭대기",
        "gym" to "체육관",
        "gallery" to "미술관",
        "exhibition" to "전시장",
    )

    private val themes = listOf(
        Theme(listOf("dawn", "walk", "birdsong"), listOf("park", "trail"), listOf("🐦", "🌅", "🚶"), 5, 10),
        Theme(listOf("shade", "breeze", "leaves"), listOf("garden", "park"), listOf("🍃", "🌳", "☁️"), 9, 17),
        Theme(listOf("tea", "steam", "teapot"), listOf("home", "cafe"), listOf("🍵", "🫖"), 7, 20),
        Theme(listOf("firstSip", "coffee", "cup"), listOf("kitchen", "cafe"), listOf("☕", "🫘"), 6, 16),
        Theme(listOf("cycling", "breeze", "pedals"), listOf("park", "riverside"), listOf("🚲", "🍃"), 7, 18),
        Theme(listOf("ripples", "swim", "goggles"), listOf("pool"), listOf("🏊", "💧", "🥽"), 7, 20),
        Theme(listOf("favoriteSong", "melody", "headphones"), listOf("home", "cafe"), listOf("🎧", "🎶"), 8, 22),
        Theme(listOf("reading", "lastPage", "bookmark"), listOf("library", "bookshop"), listOf("📖", "🔖", "📚"), 9, 19),
        Theme(listOf("basket", "tomatoes", "herbs"), listOf("market"), listOf("🧺", "🍅", "🌿"), 8, 16),
        Theme(listOf("cooking", "soup", "simmer"), listOf("kitchen"), listOf("🥣", "🥕", "🍲"), 11, 20),
        Theme(listOf("sunset", "pinkSky", "clouds"), listOf("bridge", "balcony"), listOf("🌇", "☁️"), 16, 21),
        Theme(listOf("raindrops", "umbrella", "puddles"), listOf("street", "park"), listOf("☔", "💧", "🌧️"), 7, 18),
        Theme(listOf("sketch", "pencil", "portrait"), listOf("studio", "desk"), listOf("✏️", "🎨"), 9, 21),
        Theme(listOf("sprout", "watering", "bloom"), listOf("garden", "balcony"), listOf("🌱", "💧", "🌼"), 7, 17),
        Theme(listOf("dough", "loaf", "crust"), listOf("kitchen", "bakery"), listOf("🍞", "🥖"), 6, 17),
        Theme(listOf("picnic", "grapes", "blanket"), listOf("park", "lake"), listOf("🧺", "🍇", "☀️"), 10, 16),
        Theme(listOf("waves", "shells", "sand"), listOf("beach"), listOf("🌊", "🐚"), 7, 19),
        Theme(listOf("reflection", "ripples", "stones"), listOf("lake", "riverside"), listOf("💧", "🪨"), 7, 18),
        Theme(listOf("train", "ticket", "windowSeat"), listOf("station"), listOf("🚆", "🎫"), 6, 21),
        Theme(listOf("nightSky", "moon", "stars"), listOf("hill", "balcony"), listOf("🌙", "✨", "🔭"), 18, 23),
        Theme(listOf("photo", "camera", "shadow"), listOf("street", "park"), listOf("📸", "☀️"), 8, 19),
        Theme(listOf("pottery", "clay", "bowl"), listOf("studio"), listOf("🏺", "👐"), 9, 18),
        Theme(listOf("puzzle", "corners", "lastPiece"), listOf("table", "home"), listOf("🧩", "💡"), 10, 22),
        Theme(listOf("letter", "envelope", "handwriting"), listOf("desk", "home"), listOf("✉️", "🖋️"), 8, 21),
        Theme(listOf("climbing", "footsteps", "view"), listOf("hilltop", "trail"), listOf("🥾", "⛰️"), 7, 17),
        Theme(listOf("stretching", "breathing", "balance"), listOf("home", "gym"), listOf("🧘", "🌿"), 7, 20),
        Theme(listOf("painting", "blue", "sculpture"), listOf("gallery", "exhibition"), listOf("🖼️", "🎨"), 9, 18),
        Theme(listOf("quiet", "candle", "journal"), listOf("desk", "window"), listOf("🕯️", "📓"), 17, 23),
    )

    private val arrangements = listOf(
        listOf("word"), listOf("emoji"), listOf("place", "word"), listOf("word", "emoji"),
        listOf("word", "place", "emoji"), listOf("time", "place", "word"), listOf("emoji", "word", "time"),
        listOf("word", "emoji", "place", "time"), listOf("time", "word", "place", "emoji"),
        listOf("place", "word", "emoji", "word"), listOf("emoji", "time", "word", "place"),
        listOf("word", "place", "time", "word", "emoji"), listOf("time", "emoji", "word", "place", "word", "emoji"),
    )

    /** trial.js `random(seed)`: the same 32-bit integer mixer, with JS wrap-around semantics. */
    private class Roll(private var seed: Int) {
        operator fun invoke(limit: Int): Int {
            seed += 0x6d2b79f5
            var bits = (seed xor (seed ushr 15)) * (seed or 1)
            bits = bits xor (bits + (bits xor (bits ushr 7)) * (bits or 61))
            val unsigned = (bits xor (bits ushr 14)).toLong() and 0xFFFFFFFFL
            return Math.floor(unsigned / 4294967296.0 * limit).toInt()
        }
    }

    /** trial.js `hasSample`: one or two quiet days in every nine-day block. */
    fun hasSample(day: Long): Boolean {
        val block = Math.floorDiv(day, 9L)
        val offset = (day - block * 9).toInt()
        val roll = Roll(block.toInt())
        val first = 1 + roll(7)
        if (offset == first) return false
        if (roll(2) == 0) return true
        val candidates = (1..7).filter { kotlin.math.abs(it - first) > 1 }
        return offset != candidates[roll(candidates.size)]
    }

    /** trial.js `sampleItems(key)` in Korean. */
    fun itemsOn(date: LocalDate): List<Entry> {
        val roll = Roll(date.toEpochDay().toInt())
        val theme = themes[roll(themes.size)]
        val sequence = arrangements[roll(arrangements.size)]
        val choices = mutableMapOf(
            "word" to theme.words.toMutableList(),
            "emoji" to theme.emoji.toMutableList(),
            "place" to theme.places.toMutableList(),
        )
        val items = mutableListOf<Entry>()
        for (kind in sequence) {
            val value = if (kind == "time") {
                val hour = theme.from + roll(theme.to - theme.from + 1)
                "%02d:%02d".format(hour, roll(12) * 5)
            } else {
                val pool = choices.getValue(kind)
                if (pool.isEmpty()) continue
                val token = pool.removeAt(roll(pool.size))
                if (kind == "emoji") token else words.getValue(token)
            }
            val k = when (kind) {
                "word" -> EntryKind.WORD; "emoji" -> EntryKind.EMOJI; "place" -> EntryKind.PLACE; else -> EntryKind.TIME
            }
            // Same ids as iOS: a UUID built from (day number × 16 + index).
            val id = "00000000-0000-4000-8000-%012x".format(date.toEpochDay() * 16 + items.size)
            items += Entry(id = id, kind = k, value = value)
        }
        return items
    }

    /** trial.js `firstDay`: the day after the same date three years earlier. */
    fun start(today: LocalDate): LocalDate = today.minusYears(3).plusDays(1)

    /** Every sample day from [start] to [today], as a journal. */
    fun journal(today: LocalDate = LocalDate.now()): Journal {
        var journal = Journal()
        var day = start(today)
        while (!day.isAfter(today)) {
            if (hasSample(day.toEpochDay())) journal = journal.withDay(day, itemsOn(day))
            day = day.plusDays(1)
        }
        return journal
    }
}
