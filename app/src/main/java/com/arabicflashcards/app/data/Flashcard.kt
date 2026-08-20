package com.arabicflashcards.app.data

data class Flashcard(
    val id: Int,
    val arabic: String,
    val transliteration: String,
    val english: String,
    val category: Category
)

enum class Category(val displayName: String) {
    GREETINGS("Greetings"),
    BASICS("Basics"),
    QUESTIONS("Questions"),
    NUMBERS("Numbers"),
    FOOD("Food & Drink"),
    DIRECTIONS("Directions"),
    TIME("Time"),
    EMERGENCY("Emergency")
}

object PhraseBank {
    val phrases: List<Flashcard> = listOf(
        Flashcard(1, "مرحباً", "marhaban", "Hello", Category.GREETINGS),
        Flashcard(2, "السلام عليكم", "as-salamu alaykum", "Peace be upon you (greeting)", Category.GREETINGS),
        Flashcard(3, "وعليكم السلام", "wa alaykumu s-salam", "And upon you peace (reply)", Category.GREETINGS),
        Flashcard(4, "صباح الخير", "sabah al-khayr", "Good morning", Category.GREETINGS),
        Flashcard(5, "مساء الخير", "masa' al-khayr", "Good evening", Category.GREETINGS),
        Flashcard(6, "مع السلامة", "ma'a as-salama", "Goodbye", Category.GREETINGS),
        Flashcard(7, "أهلاً وسهلاً", "ahlan wa sahlan", "Welcome", Category.GREETINGS),
        Flashcard(8, "تشرفنا", "tasharrafna", "Nice to meet you", Category.GREETINGS),

        Flashcard(9, "نعم", "na'am", "Yes", Category.BASICS),
        Flashcard(10, "لا", "la", "No", Category.BASICS),
        Flashcard(11, "شكراً", "shukran", "Thank you", Category.BASICS),
        Flashcard(12, "عفواً", "'afwan", "You're welcome / Excuse me", Category.BASICS),
        Flashcard(13, "من فضلك", "min fadlik", "Please", Category.BASICS),
        Flashcard(14, "آسف", "asif", "Sorry", Category.BASICS),
        Flashcard(15, "لا أفهم", "la afham", "I don't understand", Category.BASICS),
        Flashcard(16, "هل تتكلم الإنجليزية؟", "hal tatakallam al-injliziyya?", "Do you speak English?", Category.BASICS),
        Flashcard(17, "اسمي...", "ismi...", "My name is...", Category.BASICS),
        Flashcard(18, "أنا بخير", "ana bikhayr", "I am fine", Category.BASICS),

        Flashcard(19, "كيف حالك؟", "kayfa haluk?", "How are you?", Category.QUESTIONS),
        Flashcard(20, "ما اسمك؟", "ma ismuk?", "What is your name?", Category.QUESTIONS),
        Flashcard(21, "من أين أنت؟", "min ayna anta?", "Where are you from?", Category.QUESTIONS),
        Flashcard(22, "كم عمرك؟", "kam 'umruk?", "How old are you?", Category.QUESTIONS),
        Flashcard(23, "أين الحمام؟", "ayna al-hammam?", "Where is the bathroom?", Category.QUESTIONS),
        Flashcard(24, "بكم هذا؟", "bikam hadha?", "How much is this?", Category.QUESTIONS),
        Flashcard(25, "متى؟", "mata?", "When?", Category.QUESTIONS),
        Flashcard(26, "لماذا؟", "limadha?", "Why?", Category.QUESTIONS),

        Flashcard(27, "واحد", "wahid", "One", Category.NUMBERS),
        Flashcard(28, "اثنان", "ithnan", "Two", Category.NUMBERS),
        Flashcard(29, "ثلاثة", "thalatha", "Three", Category.NUMBERS),
        Flashcard(30, "أربعة", "arba'a", "Four", Category.NUMBERS),
        Flashcard(31, "خمسة", "khamsa", "Five", Category.NUMBERS),
        Flashcard(32, "عشرة", "'ashara", "Ten", Category.NUMBERS),
        Flashcard(33, "مئة", "mi'a", "One hundred", Category.NUMBERS),

        Flashcard(34, "ماء", "ma'", "Water", Category.FOOD),
        Flashcard(35, "خبز", "khubz", "Bread", Category.FOOD),
        Flashcard(36, "قهوة", "qahwa", "Coffee", Category.FOOD),
        Flashcard(37, "شاي", "shay", "Tea", Category.FOOD),
        Flashcard(38, "أنا جائع", "ana ja'i'", "I am hungry", Category.FOOD),
        Flashcard(39, "الفاتورة من فضلك", "al-fatura min fadlik", "The bill, please", Category.FOOD),
        Flashcard(40, "لذيذ", "ladhidh", "Delicious", Category.FOOD),

        Flashcard(41, "يمين", "yamin", "Right", Category.DIRECTIONS),
        Flashcard(42, "يسار", "yasar", "Left", Category.DIRECTIONS),
        Flashcard(43, "على طول", "'ala tul", "Straight ahead", Category.DIRECTIONS),
        Flashcard(44, "قريب", "qarib", "Near", Category.DIRECTIONS),
        Flashcard(45, "بعيد", "ba'id", "Far", Category.DIRECTIONS),
        Flashcard(46, "أين الفندق؟", "ayna al-funduq?", "Where is the hotel?", Category.DIRECTIONS),

        Flashcard(47, "اليوم", "al-yawm", "Today", Category.TIME),
        Flashcard(48, "غداً", "ghadan", "Tomorrow", Category.TIME),
        Flashcard(49, "أمس", "ams", "Yesterday", Category.TIME),
        Flashcard(50, "الآن", "al-an", "Now", Category.TIME),
        Flashcard(51, "لاحقاً", "lahiqan", "Later", Category.TIME),

        Flashcard(52, "النجدة!", "an-najda!", "Help!", Category.EMERGENCY),
        Flashcard(53, "اتصل بالطبيب", "ittasil bit-tabib", "Call a doctor", Category.EMERGENCY),
        Flashcard(54, "أنا تائه", "ana ta'ih", "I am lost", Category.EMERGENCY),
        Flashcard(55, "اتصل بالشرطة", "ittasil bish-shurta", "Call the police", Category.EMERGENCY)
    )
}
