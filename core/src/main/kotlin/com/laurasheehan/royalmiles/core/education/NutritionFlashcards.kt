package com.laurasheehan.royalmiles.core.education

/**
 * Question/answer pairs explaining the reasoning behind the numbers in NUTRITION_GUIDELINES.md
 * (ACSM/IOC/ISSN consensus), plus the per-kg nuance the source's rounded figures leave out.
 *
 * Purely educational — not wired to XP, streaks, or any completion tracking, so it stays outside
 * the boundary the rest of the nutrition feature respects.
 *
 * Answers are written tight on purpose: one idea per sentence, no throat-clearing.
 */
data class NutritionFlashcard(
    val question: String,
    val answer: String,
)

object NutritionFlashcards {
    val cards: List<NutritionFlashcard> = listOf(
        NutritionFlashcard(
            question = "Why ~20g of protein after a session — and is it scaled to me?",
            answer = "~20g maxed out muscle protein synthesis in the dose-response studies. But those " +
                "used young men averaging around 80kg. Expressed per kg it's roughly 0.25–0.4 g/kg per " +
                "feeding — about 17–27g at 67kg. So 20g is a rounded population average that happens " +
                "to fit you. The real scaling variable is lean mass, not total body weight.",
        ),
        NutritionFlashcard(
            question = "Is 20g a ceiling, or a floor?",
            answer = "A floor. The tidy \"20g plateau\" came from leg-only exercise studies. Later work " +
                "found 40g beat 20g after whole-body strength sessions — the more muscle you've just " +
                "loaded, the higher the useful dose. Protein beyond that isn't wasted either; it still " +
                "counts toward your daily total. Hit ~20g, don't cap yourself there.",
        ),
        NutritionFlashcard(
            question = "Why pair carbs with that protein?",
            answer = "Carbs restock glycogen, protein drives repair. Neither alone does both jobs. " +
                "Roughly 3-4:1 carbs to protein is the most agreed-on recovery ratio across ACSM, IOC " +
                "and ISSN.",
        ),
        NutritionFlashcard(
            question = "Why does the 30-minute window matter?",
            answer = "Exercise leaves muscle unusually insulin-sensitive and receptive to amino acids " +
                "for a short period afterward. Eating then front-loads repair instead of waiting on " +
                "whenever your next meal lands.",
        ),
        NutritionFlashcard(
            question = "Why is carb-loading 24-48 hours, not three days?",
            answer = "The three-day protocol came from research on less-trained people. A trained body " +
                "refills glycogen in 24-48 hours at 7-12 g/kg. A short boost, not a multi-day load.",
        ),
        NutritionFlashcard(
            question = "Why do carb targets climb from Base to Peak?",
            answer = "Glycogen depletes faster as sessions get longer and harder. The g/kg target " +
                "tracks training demand — matching fuel to work, not eating more because you're " +
                "\"training more\".",
        ),
        NutritionFlashcard(
            question = "What is energy availability, and why does it matter?",
            answer = "Food energy in, minus exercise energy out, per kg of fat-free mass. Below about " +
                "30 kcal/kg FFM/day is linked to menstrual disruption and reduced bone density in " +
                "female athletes. It's the clearest evidence-backed risk of under-fuelling.",
        ),
        NutritionFlashcard(
            question = "Why does this app never show a calorie target?",
            answer = "The guidelines' core principle is matching intake to training load, not holding a " +
                "fixed number. A fixed target invites exactly the under-fuelling that energy " +
                "availability warns about.",
        ),
        NutritionFlashcard(
            question = "Why keep fluid loss under 2% of body weight?",
            answer = "Both ISSN and IOC put the measurable performance drop-off there. Weighing before " +
                "and after a long run is how the guidelines suggest finding your own sweat rate — " +
                "thirst lags behind actual loss.",
        ),
        NutritionFlashcard(
            question = "Why does mid-run fuelling only matter past ~60 minutes?",
            answer = "Glycogen covers shorter efforts comfortably. Past an hour, 30-60g of carbs per " +
                "hour measurably helps. A half marathon sits right at that edge — which is why the " +
                "peak long run is worth using as fuelling practice.",
        ),
        NutritionFlashcard(
            question = "Why are iron and vitamin D flagged for endurance athletes?",
            answer = "Endurance training raises iron turnover, so deficiency builds over time. Vitamin D " +
                "risk tracks lifestyle — indoor sessions, northern latitudes, little sun. Neither is a " +
                "routine supplement; both are worth testing if in doubt.",
        ),
        NutritionFlashcard(
            question = "Why avoid high-dose antioxidant supplements?",
            answer = "No strong performance evidence, and some evidence they blunt training adaptation — " +
                "your body uses exercise-induced oxidative stress as an adaptation signal. Food sources " +
                "carry no such risk.",
        ),
        NutritionFlashcard(
            question = "Which supplements actually have evidence?",
            answer = "ISSN rates creatine, protein/EAAs, caffeine, carb-electrolyte drinks, sodium " +
                "bicarbonate and beta-alanine as effective and safe. For endurance specifically, IOC " +
                "highlights caffeine and nitrate. None offsets an inadequate daily diet.",
        ),
        NutritionFlashcard(
            question = "Why do athletes need more protein than 0.8 g/kg?",
            answer = "Training raises muscle protein breakdown and the demand for repair. The athlete " +
                "range of ~1.2-2.0 g/kg reflects that turnover. Going lower risks under-recovering " +
                "between sessions.",
        ),
        NutritionFlashcard(
            question = "Why shouldn't fat drop below 20% of intake?",
            answer = "Fat carries the fat-soluble vitamins (A, D, E, K) and essential fatty acids. Too " +
                "low risks missing those even when carbs and protein look fine. Above ~30% crowds out " +
                "the carbohydrate training needs.",
        ),
        NutritionFlashcard(
            question = "Why does the pre-race meal range 1-4 g/kg, 1-4 hours out?",
            answer = "It's a digestion trade-off. More lead time allows a bigger meal. A tight window — " +
                "an early start — calls for less, closer in, so it's digested rather than sitting heavy.",
        ),
    )
}
