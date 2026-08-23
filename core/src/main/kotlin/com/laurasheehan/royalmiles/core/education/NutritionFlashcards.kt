package com.laurasheehan.royalmiles.core.education

/**
 * Question/answer pairs explaining the reasoning behind the numbers in NUTRITION_GUIDELINES.md
 * (ACSM/IOC/ISSN consensus). Purely educational — not wired to XP, streaks, or any completion
 * tracking, so it stays outside the boundary the rest of the nutrition feature respects.
 */
data class NutritionFlashcard(
    val question: String,
    val answer: String,
)

object NutritionFlashcards {
    val cards: List<NutritionFlashcard> = listOf(
        NutritionFlashcard(
            question = "Why ~20g of protein after a session — not more, not less?",
            answer = "Muscle protein synthesis is maximally triggered by around 20g of high-quality " +
                "protein at once. Below that, you leave some repair on the table; well beyond it, the " +
                "extra mostly gets used for energy instead of adding more muscle-building signal — so " +
                "~20g is the efficient dose, not a ceiling.",
        ),
        NutritionFlashcard(
            question = "Why pair carbs with that protein, at roughly a 3-4:1 ratio?",
            answer = "Carbs restock the glycogen you just burned; protein drives repair. Neither alone " +
                "covers both jobs. A 3-4:1 carb:protein combo is the most consistently cited recovery " +
                "ratio across ACSM, IOC, and ISSN guidance — more agreed-upon than most other numbers " +
                "in the field.",
        ),
        NutritionFlashcard(
            question = "Why does the 30-minute post-exercise window matter?",
            answer = "Exercise itself opens a short period where muscle is unusually insulin-sensitive " +
                "and receptive to amino acids. Eating protein inside that window front-loads repair " +
                "instead of leaving it to whenever your next meal happens to land.",
        ),
        NutritionFlashcard(
            question = "Why does carb-loading only take 24-48 hours, not the old \"three days\"?",
            answer = "The three-day loading protocol came from research in less-trained individuals. " +
                "Current evidence shows a well-trained body can maximise glycogen stores in 24-48 " +
                "hours at 7-12g of carbs per kg of body weight — a moderate boost, not a multi-day " +
                "mega-load.",
        ),
        NutritionFlashcard(
            question = "Why does the carb target scale up through Base → Build → Peak?",
            answer = "Carbohydrate is the body's preferred fuel for the *effort level* of a session, not " +
                "just its distance. As long runs get longer and harder, glycogen depletes faster, so " +
                "the g/kg target climbs with training load — it's about matching fuel to demand, not " +
                "eating more because you're \"training more\".",
        ),
        NutritionFlashcard(
            question = "What is \"estimated Energy Availability\" (estEA), and why does it matter?",
            answer = "estEA = food energy in, minus exercise energy burned, per kg of fat-free mass. " +
                "Below roughly 30 kcal/kg FFM/day is linked to menstrual disruption and reduced bone " +
                "density in female athletes — a real, well-evidenced risk of under-fuelling relative " +
                "to training load, not just a performance number.",
        ),
        NutritionFlashcard(
            question = "Why doesn't this app ever show a calorie target or \"remaining\" number?",
            answer = "The guidelines' core principle is that energy intake should match energy expended " +
                "in training, not sit at a fixed number chasing a deficit. A fixed calorie target " +
                "invites exactly the under-fuelling risk estEA warns about — so the app shows what was " +
                "logged, and phase-aware ranges, and stops there.",
        ),
        NutritionFlashcard(
            question = "Why does fluid loss need to stay under 2% of body weight during a run?",
            answer = "Both ISSN and IOC use 2% body-weight loss as the threshold where performance " +
                "measurably starts to decline. Weighing yourself before and after a long run is the " +
                "guidelines' recommended way to work out your own sweat rate, since thirst alone lags " +
                "behind actual fluid loss.",
        ),
        NutritionFlashcard(
            question = "Why does during-run fuelling only matter past about 45-60 minutes?",
            answer = "Glycogen stores comfortably cover shorter efforts. Past roughly 60 minutes, " +
                "stores start running low enough that topping up — 30-60g of carbs per hour — measurably " +
                "helps. A true half-marathon finish sits right around where this starts to matter, which " +
                "is why race-day fuelling is worth rehearsing on the peak long run.",
        ),
        NutritionFlashcard(
            question = "Why are iron and vitamin D specifically flagged for endurance athletes?",
            answer = "Endurance training increases iron turnover, raising deficiency risk over time. " +
                "Vitamin D risk comes from lifestyle factors that often overlap with serious training — " +
                "indoor sessions, higher latitudes, limited sun exposure. Neither is a routine " +
                "supplement recommendation; both are worth having checked if there's any doubt.",
        ),
        NutritionFlashcard(
            question = "Why does the guidance advise against high-dose antioxidant supplements?",
            answer = "There's no strong evidence they improve performance, and some evidence suggests " +
                "high doses can actually blunt the training adaptations you're working for — your body " +
                "uses a certain amount of exercise-induced oxidative stress as a signal to adapt. Food " +
                "sources (fruit, veg, whole grains) don't carry that risk.",
        ),
        NutritionFlashcard(
            question = "Which supplements actually have strong evidence behind them?",
            answer = "ISSN rates creatine, protein/EAAs, caffeine, carb-electrolyte drinks, sodium " +
                "bicarbonate, and beta-alanine as \"apparently effective, generally safe\". For endurance " +
                "specifically, IOC highlights caffeine and nitrate as the most relevant. None of it " +
                "compensates for an inadequate daily diet — food comes first.",
        ),
        NutritionFlashcard(
            question = "Why do athletes need more protein than the general 0.8g/kg guideline?",
            answer = "Training — especially higher-volume, intense training — increases muscle protein " +
                "breakdown and the demand for repair and adaptation. The athlete range of roughly " +
                "1.2-2.0g/kg/day reflects that extra turnover; going lower risks under-recovering " +
                "between sessions.",
        ),
        NutritionFlashcard(
            question = "Why shouldn't fat drop below 20% of total energy intake?",
            answer = "Fat carries the fat-soluble vitamins (A, D, E, K) and essential fatty acids the " +
                "body can't make itself. Dropping too low risks missing those, even if calories and " +
                "carbs/protein look fine on paper. High-fat diets (over 30%) aren't recommended either — " +
                "they crowd out the carbohydrate training actually needs.",
        ),
        NutritionFlashcard(
            question = "Why does the pre-race meal window vary so much — 1-4g/kg, 1-4 hours before?",
            answer = "It's a trade-off between digestion time and fuel: more lead time before the start " +
                "allows a bigger meal, since there's longer for it to digest. A tight window — an early " +
                "race start, for instance — calls for a smaller amount eaten closer in, so it's " +
                "digested rather than sitting heavy at the start line.",
        ),
    )
}
