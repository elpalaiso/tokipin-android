package com.tokipin.app.data

import java.time.LocalDate

/** Pure, deterministic port of trial.js. The caller owns the returned journal and decides where it lives. */
internal object SampleGenerator {
    private data class Theme(val words: List<String>, val places: List<String>, val emoji: List<String>, val from: Int, val to: Int)

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

    /** trial.js `sampleItems(key, starter)` in the selected language. */
    fun itemsOn(date: LocalDate, words: Map<String, String>, starter: Boolean = false): List<Entry> {
        val roll = Roll(date.toEpochDay().toInt())
        val theme = themes[roll(themes.size)]
        val sequence = if (starter) listOf("word", "emoji", "place", "time") else arrangements[roll(arrangements.size)]
        val choices = mutableMapOf(
            "word" to theme.words.toMutableList(),
            "emoji" to theme.emoji.toMutableList(),
            "place" to theme.places.toMutableList(),
        )
        val items = mutableListOf<Entry>()
        for (kind in sequence) {
            val value = if (kind == "time") {
                val hour = theme.from + roll(theme.to - theme.from + 1)
                "%02d:%02d".format(java.util.Locale.ROOT, hour, roll(12) * 5)
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
            val id = "00000000-0000-4000-8000-%012x".format((date.toEpochDay() * 16 + items.size) and 0xFFFFFFFFFFFFL)
            items += Entry(id = id, kind = k, value = value)
        }
        return items
    }

    /** trial.js `firstDay`: the day after the same date [years] years earlier (three on the homepage). */
    fun start(today: LocalDate, years: Long = 3): LocalDate = today.minusYears(years).plusDays(1)

    /** Every sample day from [start] to [today], as a journal. */
    fun journal(today: LocalDate, words: Map<String, String>, years: Long = 3): Journal {
        // Built in one map: adding day by day with withDay copied the whole map each time.
        val days = LinkedHashMap<String, List<Entry>>()
        val stamps = LinkedHashMap<String, String>()
        val now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        var day = start(today, years)
        while (!day.isAfter(today)) {
            if (hasSample(day.toEpochDay())) {
                val items = itemsOn(day, words)
                if (items.isNotEmpty()) { days[day.toString()] = items; stamps[day.toString()] = now }
            }
            day = day.plusDays(1)
        }
        return Journal(days, stamps)
    }

}
