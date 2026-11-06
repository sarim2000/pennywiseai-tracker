package com.pennywiseai.tracker.domain.conscience

/**
 * Every word the conscience can say. Plain Kotlin rather than res/: the user picks
 * the voice independently of the device locale.
 *
 * Writing rules: lines state only what a [ConscienceFact] proved (days, category),
 * never an amount. Food & Dining covers eating out and coffee too, so food lines
 * never claim the user ordered in. [ConscienceLine.feminine] is filled wherever a
 * Jordanian second-person form is marked in writing. The first line of each pool is
 * the one the opt-in preview shows, so keep the strongest first. Ids are stored in
 * the no-repeat log: never reuse or renumber one.
 */
object ConscienceLines {

    fun pool(voice: ConscienceVoice, fact: ConscienceFact): List<ConscienceLine> = when (voice) {
        ConscienceVoice.JORDANIAN -> when (fact) {
            is ConscienceFact.RecordDay -> JO_RECORD_DAY
            is ConscienceFact.Comeback -> if (fact.category == ConscienceCategory.FOOD) JO_FOOD_COMEBACK else JO_COMEBACK
        }
        ConscienceVoice.ENGLISH -> when (fact) {
            is ConscienceFact.RecordDay -> EN_RECORD_DAY
            is ConscienceFact.Comeback -> if (fact.category == ConscienceCategory.FOOD) EN_FOOD_COMEBACK else EN_COMEBACK
        }
    }

    fun render(line: ConscienceLine, voice: ConscienceVoice, address: ConscienceAddress, fact: ConscienceFact): String {
        val template = if (address == ConscienceAddress.FEMININE) line.feminine else line.masculine
        val withCategory = template.replace("{category}", categoryNoun(voice, fact.category))
        return if (fact is ConscienceFact.Comeback) {
            withCategory.replace("{days}", daysPhrase(voice, fact.days))
        } else {
            withCategory
        }
    }

    fun title(voice: ConscienceVoice): String = when (voice) {
        ConscienceVoice.JORDANIAN -> "ضميرك"
        ConscienceVoice.ENGLISH -> "Your conscience"
    }

    // The two action labels are said by the user to the conscience, so they aren't gendered on the user.
    fun lessOftenLabel(voice: ConscienceVoice): String = when (voice) {
        ConscienceVoice.JORDANIAN -> "خف شوي"
        ConscienceVoice.ENGLISH -> "Ease up"
    }

    fun pauseLabel(voice: ConscienceVoice): String = when (voice) {
        ConscienceVoice.JORDANIAN -> "وقّف"
        ConscienceVoice.ENGLISH -> "Pause"
    }

    fun lessOftenConfirmation(voice: ConscienceVoice): String = when (voice) {
        ConscienceVoice.JORDANIAN -> "ماشي، رح أخف شوي… بس عيني عليك 👀"
        ConscienceVoice.ENGLISH -> "Fine, I'll ease up. Still watching, though 👀"
    }

    fun pausedConfirmation(voice: ConscienceVoice): String = when (voice) {
        ConscienceVoice.JORDANIAN -> "ماشي، رح أسكت 🤐 الرجعة من الإعدادات"
        ConscienceVoice.ENGLISH -> "Fine. Not a word 🤐 Settings brings me back."
    }

    fun lockScreenText(voice: ConscienceVoice): String = when (voice) {
        ConscienceVoice.JORDANIAN -> "عندي إلك كلمتين 👀"
        ConscienceVoice.ENGLISH -> "Psst. A quick word."
    }

    fun samples(voice: ConscienceVoice, address: ConscienceAddress): List<String> = listOf(
        ConscienceFact.Comeback(ConscienceCategory.FOOD, days = 3),
        ConscienceFact.Comeback(ConscienceCategory.SHOPPING, days = 12),
        ConscienceFact.RecordDay(ConscienceCategory.FOOD),
    ).map { fact -> render(pool(voice, fact).first(), voice, address, fact) }

    /** Latin digits in Arabic too, matching the rest of the app (see utils/LatinDigits.kt). */
    private fun daysPhrase(voice: ConscienceVoice, days: Int): String = when (voice) {
        ConscienceVoice.JORDANIAN -> if (days <= 10) "$days أيام" else "$days يوم"
        ConscienceVoice.ENGLISH -> "$days days"
    }

    private fun categoryNoun(voice: ConscienceVoice, category: ConscienceCategory): String = when (voice) {
        ConscienceVoice.JORDANIAN -> when (category) {
            ConscienceCategory.FOOD -> "أكل"
            ConscienceCategory.ENTERTAINMENT -> "ترفيه"
            ConscienceCategory.SHOPPING -> "تسوق"
        }
        ConscienceVoice.ENGLISH -> when (category) {
            ConscienceCategory.FOOD -> "food"
            ConscienceCategory.ENTERTAINMENT -> "fun"
            ConscienceCategory.SHOPPING -> "shopping"
        }
    }

    private val JO_FOOD_COMEBACK = listOf(
        ConscienceLine("jo_food_comeback_01", "ما تركت إلا {days} وصرت راجع 😂", "ما تركت إلا {days} وصرت راجعة 😂"),
        ConscienceLine("jo_food_comeback_02", "انتهت المقاطعة بعد {days} 🍔"),
        ConscienceLine("jo_food_comeback_03", "{days} وبعدين؟ اشتقنا للمنيو؟"),
        ConscienceLine("jo_food_comeback_04", "{days} وقلنا تغيّر… طلعنا غلطانين", "{days} وقلنا تغيّرت… طلعنا غلطانين"),
        ConscienceLine("jo_food_comeback_05", "قلنا {days} بداية موفقة… طلعت النهاية كمان 😂"),
        ConscienceLine("jo_food_comeback_06", "بعد {days} فراق، المنيو ما نسيك 😏", "بعد {days} فراق، المنيو ما نسيكي 😏"),
        ConscienceLine("jo_food_comeback_07", "{days} بطولة… وريحة أكل وحدة خلصت عليها"),
        ConscienceLine("jo_food_comeback_08", "رجعت المي لمجاريها بينك وبين الأكل بعد {days} زعل"),
        ConscienceLine("jo_food_comeback_09", "فرحتنا فيك طولت {days} بس 😂", "فرحتنا فيكي طولت {days} بس 😂"),
        ConscienceLine("jo_food_comeback_10", "الأكل كسب الجولة بعد {days} 🥊"),
        ConscienceLine("jo_food_comeback_11", "المنيو سامحك بعد {days} 🤝", "المنيو سامحكي بعد {days} 🤝"),
        ConscienceLine("jo_food_comeback_12", "{days} صبر، وهسا صحتين على قلبك 😏"),
    )

    // Shared by ترفيه and تسوق: ال{category} / لل{category} must read right with both.
    private val JO_COMEBACK = listOf(
        ConscienceLine("jo_comeback_02", "قال بطّلت ال{category} قال! {days} بس 😂"),
        ConscienceLine("jo_comeback_01", "{days} صمود… وانتهى الفيلم 🍿"),
        ConscienceLine(
            "jo_comeback_03",
            "ال{category} بسلم عليك وبقولك: {days} بس؟ 😏",
            "ال{category} بسلم عليكي وبقولك: {days} بس؟ 😏",
        ),
        ConscienceLine("jo_comeback_04", "ال{category} اتصل؟ {days} وجيت ركض 🏃", "ال{category} اتصل؟ {days} وجيتي ركض 🏃"),
        ConscienceLine("jo_comeback_05", "{days} عقل، وبعدين قررت تعيش 😂", "{days} عقل، وبعدين قررتي تعيشي 😂"),
        ConscienceLine("jo_comeback_06", "{days} غياب عن ال{category}… وما هان عليك تطوّل 😏"),
        ConscienceLine("jo_comeback_07", "{days} حلم حلو… وصحينا على ال{category} 😴"),
        ConscienceLine("jo_comeback_08", "تقاعدك من ال{category} طوّل {days}"),
        ConscienceLine("jo_comeback_09", "ما صدقت تخلص {days} ورجعت لل{category}"),
        ConscienceLine("jo_comeback_10", "{days} بدون {category}؟ لأ، كتير عليك 😂"),
        ConscienceLine("jo_comeback_11", "{days} راحوا، ورجعت لل{category} ولا كأنه إشي صار"),
        ConscienceLine(
            "jo_comeback_12",
            "ال{category} نزّل إعلان مفقود من {days}… ولقاك",
            "ال{category} نزّل إعلان مفقودة من {days}… ولقاكي",
        ),
    )

    private val JO_RECORD_DAY = listOf(
        ConscienceLine("jo_record_day_01", "كمل كمل 😂", "كملي كملي 😂"),
        ConscienceLine("jo_record_day_02", "أعلى يوم {category} من شهرين… مبروك 🎉"),
        ConscienceLine("jo_record_day_03", "كسرت رقمك القياسي تبع شهرين 🏆"),
        ConscienceLine("jo_record_day_04", "يوم {category} تاريخي… بس تاريخ آخر شهرين 😂"),
        ConscienceLine("jo_record_day_05", "أنا ما شفت إشي… بس أعلى يوم {category} من شهرين 🙈"),
        ConscienceLine("jo_record_day_06", "شهرين تسخين، واليوم المباراة ⚽"),
        ConscienceLine("jo_record_day_07", "هاليوم بدو برواز: أعلى يوم {category} من شهرين 🖼️"),
        ConscienceLine("jo_record_day_08", "من شهرين ما شفنا يوم {category} زي هيك. كمان مرة! 👏"),
        ConscienceLine("jo_record_day_09", "ولا يوم {category} بآخر شهرين لحق اليوم. شاطر", "ولا يوم {category} بآخر شهرين لحق اليوم. شاطرة"),
        ConscienceLine("jo_record_day_10", "ميدالية ذهبية بال{category} 🥇 أعلى يوم من شهرين"),
        ConscienceLine("jo_record_day_11", "المدرجات واقفة 👏 أعلى يوم {category} من شهرين"),
        ConscienceLine("jo_record_day_12", "من شهرين وأنا بستنى يوم {category} زي هاد… وإجا"),
    )

    private val EN_FOOD_COMEBACK = listOf(
        ConscienceLine("en_food_comeback_01", "{days} strong. Anyway, what are we having?"),
        ConscienceLine("en_food_comeback_02", "A brave {days}. They'll write songs. Short ones."),
        ConscienceLine("en_food_comeback_03", "I told everyone you'd last a week. You lasted {days}."),
        ConscienceLine("en_food_comeback_04", "You lasted {days}. The fries never doubted you."),
        ConscienceLine("en_food_comeback_05", "{days} of willpower, then someone said \"just a bite\"."),
        ConscienceLine("en_food_comeback_06", "Scientists now put the half-life of your resolve at {days}."),
        ConscienceLine("en_food_comeback_07", "Undefeated for {days}. Then a menu happened."),
        ConscienceLine("en_food_comeback_08", "You and the menu: {days} apart. Cute while it lasted."),
        ConscienceLine("en_food_comeback_09", "Welcome back. The menu kept your seat warm for {days}."),
        ConscienceLine("en_food_comeback_10", "Food took you back after {days}. No questions asked."),
        ConscienceLine("en_food_comeback_11", "{days} later and the menu still knows your name."),
        ConscienceLine("en_food_comeback_12", "Those {days} were an era. It's over now 🍔"),
    )

    // Shared by "fun" and "shopping": {category} must read right with both.
    private val EN_COMEBACK = listOf(
        ConscienceLine("en_comeback_01", "You quit {category} for {days}. Inspiring, briefly."),
        ConscienceLine("en_comeback_02", "Your retirement from {category} lasted {days}. Nice speech."),
        ConscienceLine("en_comeback_03", "That was a lovely {days}. Anyway, {category}."),
        ConscienceLine("en_comeback_04", "You and {category} were on a break. For {days}."),
        ConscienceLine("en_comeback_05", "You and {category} stared each other down for {days}. You blinked."),
        ConscienceLine("en_comeback_06", "The {category} ban lasted {days}. Repeal was unanimous."),
        ConscienceLine("en_comeback_07", "{days} of being sensible. It never really suited you."),
        ConscienceLine("en_comeback_08", "The experiment lasted {days}. Result: {category}."),
        ConscienceLine("en_comeback_09", "Nobody's counting. Fine, I was. {days}."),
        ConscienceLine("en_comeback_10", "{days} without {category}. Movie rights are still available 🍿"),
        ConscienceLine("en_comeback_11", "Back to {category} after {days}. We'll say nothing. Loudly."),
        ConscienceLine("en_comeback_12", "Your card's holiday lasted {days}. It sends regards 💳"),
    )

    private val EN_RECORD_DAY = listOf(
        ConscienceLine("en_record_day_01", "No, no, keep going."),
        ConscienceLine("en_record_day_02", "Biggest {category} day in two months. Speech! 🎉"),
        ConscienceLine("en_record_day_03", "Today's a historic {category} day. History being the last two months."),
        ConscienceLine("en_record_day_04", "Best {category} day in two months. Not sure \"best\" is the word."),
        ConscienceLine("en_record_day_05", "I saw nothing. Except the biggest {category} day in two months 🙈"),
        ConscienceLine("en_record_day_06", "This one's going on the fridge. Biggest {category} day in two months."),
        ConscienceLine("en_record_day_07", "Gold medal in {category}. Field: the last two months 🥇"),
        ConscienceLine("en_record_day_08", "The last two months called. They concede."),
        ConscienceLine("en_record_day_09", "No {category} day in two months has touched today. Proud of you, weirdly."),
        ConscienceLine("en_record_day_10", "Two months of warm-up, and today's the main event."),
        ConscienceLine("en_record_day_11", "Summit of the last two months of {category}. How's the view?"),
        ConscienceLine("en_record_day_12", "Two-month {category} leaderboard update: today takes first."),
    )
}
