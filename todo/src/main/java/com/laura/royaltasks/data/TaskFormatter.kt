package com.laura.royaltasks.data

/**
 * Tidies a typed task into a clean to-do line: sentence case, single spaces,
 * no trailing full stop, "i" → "I". Names it doesn't recognise are left as
 * typed. Fixes the user makes in Edit are learned (lowercase word → preferred
 * form) and applied to every task after that.
 */
object TaskFormatter {

    fun format(raw: String, learned: Map<String, String> = emptyMap()): String {
        var text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return text

        // ALL CAPS input reads as shouting; treat it as lowercase and rebuild.
        val letters = text.filter { it.isLetter() }
        if (letters.length > 4 && letters.all { it.isUpperCase() }) text = text.lowercase()

        // No space before punctuation, one space after a comma.
        text = text.replace(Regex("\\s+([,.!?;:])"), "$1")
            .replace(Regex(",(?=\\p{L})"), ", ")

        val tokens = text.split(" ")
        var sentenceStart = true
        var afterStrayStop = false
        val out = tokens.mapIndexed { i, token ->
            // A to-do is one line, so a full stop mid-line is almost always the
            // keyboard's double-space shortcut: drop it, and the capital the
            // keyboard added after it.
            val stray = i < tokens.lastIndex && token.endsWith(".") && !isAbbreviation(token)
            val formatted = formatToken(
                if (stray) token.dropLast(1) else token,
                sentenceStart,
                learned,
                lowerCapital = afterStrayStop
            )
            afterStrayStop = stray
            if (formatted.any { it.isLetterOrDigit() }) {
                sentenceStart = formatted.last() in "!?"
            }
            formatted
        }

        // A to-do isn't a sentence: drop trailing full stops, keep ? and !.
        val tidied = out.joinToString(" ").trimEnd('.', ' ', ',', ';', ':')
        if (tidied.isEmpty()) return text
        return if (isQuestion(tidied)) "$tidied?" else tidied
    }

    private fun isAbbreviation(token: String): Boolean {
        val bare = token.dropLast(1)
        val core = WORD.find(bare)?.value?.lowercase() ?: return true
        return core.length == 1 || bare.contains('.') || core in ABBREVIATIONS || core.all { it.isDigit() }
    }

    /** "Does this work" → needs a "?". Only when it opens with a question word. */
    private fun isQuestion(text: String): Boolean {
        if (text.last() in "?!") return false
        val words = words(text)
        return words.size >= 2 && words.first().lowercase() in QUESTION_STARTS
    }

    private fun formatToken(
        token: String,
        sentenceStart: Boolean,
        learned: Map<String, String>,
        lowerCapital: Boolean = false
    ): String {
        val match = WORD.find(token) ?: return token
        val core = match.value
        val lower = core.lowercase()
        val fixed = learned[lower] ?: PRONOUN_I[lower] ?: when {
            lower in PROPER -> PROPER.getValue(lower)
            // Mid-sentence capitals on everyday words come from Title Case typing.
            isCapitalised(core) && lower in COMMON && !sentenceStart -> lower
            // The keyboard's capital after a dropped stray full stop.
            isCapitalised(core) && lowerCapital -> lower
            else -> core
        }
        val cased = if (sentenceStart && fixed.first().isLowerCase() && !hasInnerCapital(fixed)) {
            fixed.replaceFirstChar { it.uppercaseChar() }
        } else {
            fixed
        }
        return token.replaceRange(match.range, cased)
    }

    /**
     * Word-level fixes between a task's old title and the user's edit: casing
     * changes (huel → Huel) and small spelling fixes (twinnings → Twinings).
     * Only compares when the word count is unchanged, so rewording a task
     * teaches nothing.
     */
    fun learn(before: String, after: String): Map<String, String> {
        val old = words(before)
        val new = words(after)
        if (old.size != new.size) return emptyMap()
        val learned = mutableMapOf<String, String>()
        old.indices.forEach { i ->
            val a = old[i]
            val b = new[i]
            if (a == b) return@forEach
            val caseOnly = a.equals(b, ignoreCase = true)
            // The first word's capital is sentence case, not a preference.
            if (caseOnly && i > 0) learned[a.lowercase()] = b
            if (!caseOnly && a.length >= 5 && b.length >= 5 &&
                editDistance(a.lowercase(), b.lowercase()) <= 2
            ) {
                learned[a.lowercase()] = b
                learned[b.lowercase()] = b
            }
        }
        return learned
    }

    private val WORD = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*")

    private fun words(text: String): List<String> = WORD.findAll(text).map { it.value }.toList()

    private fun isCapitalised(word: String) =
        word.first().isUpperCase() && word.drop(1).all { !it.isLetter() || it.isLowerCase() }

    private fun hasInnerCapital(word: String) = word.drop(1).any { it.isUpperCase() }

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private val ABBREVIATIONS = setOf("dr", "mr", "mrs", "ms", "st", "vs", "etc", "approx", "no", "jr", "sr")

    // "do", "have" and "will" are left out: "Do taxes", "Have lunch", "Will to sign".
    private val QUESTION_STARTS = setOf(
        "does", "did", "is", "are", "was", "were", "am", "can", "could", "should",
        "would", "shall", "has", "what", "when", "where", "why", "how", "who", "which", "whose"
    )

    private val PRONOUN_I = mapOf(
        "i" to "I", "i'm" to "I'm", "i'll" to "I'll", "i've" to "I've", "i'd" to "I'd",
        "im" to "I'm", "ive" to "I've"
    )

    // Always capitalised. "may" and "march" are left out — too often plain words.
    private val PROPER: Map<String, String> = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        "January", "February", "April", "June", "July", "August", "September",
        "October", "November", "December", "Christmas", "Easter", "Halloween",
        "Ireland", "Scotland", "England", "Wales", "Britain", "UK", "USA", "Spain",
        "France", "Italy", "Portugal", "Germany", "Greece", "Egypt", "Dubai", "America",
        "Canada", "Australia", "Europe", "London", "Dublin", "Edinburgh", "Glasgow",
        "Belfast", "Manchester", "Paris", "Cairo", "Amazon", "Tesco", "Sainsbury's",
        "Asda", "Aldi", "Lidl", "Boots", "Ikea", "Zara", "Garmin", "Strava", "Huel",
        "Twinings", "WhatsApp", "iPhone", "Instagram", "Facebook", "YouTube", "Google",
        "Gmail", "Netflix", "Spotify", "Uber", "Airbnb", "Ryanair", "Aer", "Lingus",
        "Easyjet", "HMRC", "NHS", "GP", "MOT", "TV", "PT", "PB", "PR", "VAT", "ID",
        "Dr", "Mr", "Mrs", "Ms"
    ).associateBy { it.lowercase() } + ("twinnings" to "Twinings")

    // Everyday to-do words that only get a capital from Title Case typing.
    private val COMMON: Set<String> = """
        a an the and or but for nor so yet of to in on at by with from into onto off up
        down out over under about after before between through during without within
        my your our their his her its this that these those some any all each every
        new old next last first second other more less few many much one two three
        buy get book pay call ring text message email reply send post order pick drop
        collect return renew cancel change check clean clear fix make cook bake wash
        iron fold tidy sort plan prep write read sign print scan file submit apply
        update upload download install back up chase confirm arrange schedule
        organise organize visit meet see go take bring put find look ask tell remind
        give lend borrow share move start finish stop try sell hire rent review train
        run walk swim stretch lift
        flight flights ticket tickets hotel hotels holiday trip trips train trains bus
        taxi car cars bike parking passport visa insurance bill bills invoice invoices
        charge charges fee fees rent mortgage bank account card cards money cash tax
        payment payments portion share deposit refund receipt receipts pension
        appointment appointments doctor dentist physio vet haircut nails eyebrows
        meeting meetings call calls session sessions client clients class classes
        programme program plan plans workout workouts gym kit shoes trainers
        food groceries shopping milk bread eggs tea coffee water protein shake shakes
        snacks dinner lunch breakfast meal meals recipe veg fruit chicken rice pasta
        present presents gift gifts birthday card wedding party flowers
        house home flat room kitchen bathroom bedroom garden laundry bins bin dishes
        boiler plumber electrician landlord letter letters parcel parcels package
        form forms document documents photo photos video videos website blog email
        emails contract contracts report reports notes list lists stuff things
        mum dad sister brother friend friends family baby kids kid dog cat
        today tomorrow tonight morning afternoon evening week weekend month year
    """.trim().split(Regex("\\s+")).toSet()
}
