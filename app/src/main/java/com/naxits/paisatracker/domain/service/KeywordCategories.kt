package com.naxits.paisatracker.domain.service

/** The category a keyword in an entry's text points at, and the word that pointed there. */
data class KeywordCategory(
    val category: String,
    val subcategory: String?,
    val keyword: String
)

/**
 * Finds a category from ordinary words in the payee name or the note: "pizza", "filling station",
 * "electronics". It complements the brand list in `CategoryMapping`, which knows companies by name
 * but not what a local shop sells, and it is the only one that also picks a sub-category.
 *
 * Only the payee name and the description are read. The rest of a bank message is boilerplate
 * ("Sent", "Bill", "Card", "Transfer") that would match everything.
 */
object KeywordCategories {

    private class Rule(
        val category: String,
        val subcategory: String?,
        val keywords: List<String>,
        val excludes: List<String> = emptyList()
    )

    // Order matters: the first rule with a matching word wins, so the more specific rules come first
    private val RULES: List<Rule> = listOf(
        // Transport
        Rule("Transport", "Fuel", listOf("filling station", "fuel station", "petrol pump", "petrol", "diesel", "fuel", "petroleum")),
        Rule("Transport", "FASTag", listOf("fastag")),
        Rule("Transport", "Bus", listOf("hrtc", "roadways", "redbus", "state transport")),
        Rule("Transport", "Parking", listOf("parking")),
        Rule("Transport", "Cab", listOf("taxi", "cab", "cabs")),
        Rule("Travel", null, listOf("ixigo", "irctc", "makemytrip", "goibibo", "travels", "tours")),

        // Insurance and medical come before the shop words, so "health insurance" is not "health"
        Rule("Insurance", null, listOf("insurance", "assurance")),
        Rule("Medical", "Medicines", listOf("pharmacy", "chemist", "medical store", "medicos", "medicals")),
        Rule("Medical", "Hospital", listOf("hospital", "nursing home")),
        Rule("Medical", "Clinic", listOf("clinic")),
        Rule("Medical", "Dentist", listOf("dental", "dentist")),
        Rule("Medical", "Lab test", listOf("diagnostic", "diagnostics", "pathology")),
        Rule("Medical", null, listOf("health", "healthcare", "wellness"), excludes = listOf("bank")),

        // Food
        Rule("Food & Drinks", "Pizza", listOf("pizza", "pizzeria")),
        Rule("Food & Drinks", "Tea & Coffee", listOf("cafe", "coffee", "chai", "tea stall")),
        Rule("Food & Drinks", "Sweets", listOf("sweets", "mithai", "halwai")),
        Rule("Food & Drinks", "Fast Food", listOf("burger", "momos", "fast food", "chowmein", "noodles")),
        Rule("Food & Drinks", "Tiffin", listOf("tiffin", "biryani")),
        Rule("Food & Drinks", "Beverages", listOf("juice", "shake")),
        Rule("Food & Drinks", "Eating out", listOf("restaurant", "dhaba", "food court", "canteen", "bhojnalaya", "bhojanalay", "eatery")),
        Rule("Groceries", "Bakery", listOf("bakery", "bakers")),
        Rule("Groceries", "Dairy", listOf("dairy", "milk")),
        Rule("Groceries", "Vegetables", listOf("vegetables", "sabzi")),
        Rule("Groceries", "Fruits", listOf("fruits", "fruit")),
        Rule("Groceries", "Meat", listOf("chicken", "mutton", "meat")),
        Rule("Groceries", null, listOf("general store", "kirana", "provision", "supermarket", "super market", "grocery", "groceries")),

        // Shopping
        Rule("Shopping", "Electronics", listOf("electronics", "communication", "mobiles", "mobile shop", "mobile store", "computers")),
        Rule("Shopping", "Footwear", listOf("footwear", "shoes", "shoe", "chappal")),
        Rule("Shopping", "Clothes", listOf("garments", "clothing", "clothes", "textile", "textiles", "boutique", "fashion", "readymade", "cloth house")),
        Rule("Shopping", "Jewellery", listOf("jewellers", "jewellery", "jewelers", "jewelry", "sarraf")),
        Rule("Shopping", "Books", listOf("pustak", "bookstore", "book store", "books")),
        Rule("Shopping", "Stationery", listOf("stationery", "stationers")),
        Rule("Shopping", "Furniture", listOf("furniture")),
        Rule("Shopping", "Appliances", listOf("appliances")),
        Rule("Shopping", "Glasses", listOf("optical", "opticals")),
        Rule("Shopping", "Toys", listOf("toys")),
        Rule("Shopping", null, listOf("gift", "gifts", "gift center", "gift centre")),

        // Home and bills
        Rule("Home", "Painting", listOf("paints", "paint house")),
        Rule("Home", "Construction", listOf("cement", "building material", "building materials", "sanitary")),
        Rule("Home", "Pest-control", listOf("pest control")),
        Rule("Home", "Upkeep", listOf("hardware")),
        Rule("Bill", "Gas", listOf("lpg", "gas agency", "indane", "gramin vitran", "gramin vitr")),
        Rule("Bill", "Electricity", listOf("electricity", "vidyut", "hpseb")),
        Rule("Bill", "Internet", listOf("broadband")),
        Rule("Bill", "Water", listOf("water supply", "jal shakti")),

        // Entertainment and subscriptions
        Rule("Entertainment", "Movies", listOf("cinema", "movie", "movies", "multiplex")),
        Rule("Subscription", "Disney Plus", listOf("hotstar", "jiohotstar")),
        Rule("Subscription", "Google Play", listOf("google play", "googleplay")),
        Rule("Subscription", "Software", listOf("github")),
        Rule("Services", null, listOf("godaddy", "hostinger", "namecheap", "hosting")),

        // Services
        Rule("Services", "Tailor", listOf("tailor", "tailors")),
        Rule("Services", "Laundry", listOf("laundry", "dry clean", "dry cleaners")),
        Rule("Services", "Courier", listOf("courier", "couriers")),
        Rule("Services", "Mechanic", listOf("garage", "mechanic", "automobiles")),
        Rule("Services", "Xerox", listOf("xerox", "photostat")),
        Rule("Services", "Photographer", listOf("photography", "photo studio")),
        Rule("Services", "Vehicle Wash", listOf("car wash")),
        Rule("Services", "Electrician", listOf("electrician")),
        Rule("Services", "Plumber", listOf("plumber")),
        Rule("Services", "Repair", listOf("repair", "repairs", "service centre", "service center")),

        // Other
        Rule("Donation", null, listOf("trust", "ashram", "samiti", "mandir", "temple", "foundation", "gaushala", "charitable"), excludes = listOf("bank", "finance")),
        Rule("Children", "School Fee", listOf("school", "vidyalaya")),
        Rule("Children", "Tuition Fee", listOf("tuition", "coaching")),
        Rule("Children", "College Fee", listOf("college", "university")),
        Rule("Fitness", "Gym", listOf("gym")),
        Rule("Fitness", null, listOf("fitness", "yoga")),
        Rule("Pet Care", "Vet", listOf("vet", "veterinary")),
        Rule("Pet Care", null, listOf("pet shop", "petshop", "pets")),
        Rule("Events", "Wedding", listOf("wedding", "banquet", "marriage palace")),
        Rule("Events", null, listOf("catering", "tent house", "decorators")),
        Rule("Top-up", null, listOf("recharge", "recharges"))
    )

    private val wordRegexes = HashMap<String, Regex>()

    /** The first rule matching a word in [merchantName] or [description], or null when none does. */
    fun match(merchantName: String, description: String? = null): KeywordCategory? {
        val text = "$merchantName ${description.orEmpty()}".lowercase()
        // "Shaktifillingstation" has no spaces, so a phrase is also looked for with the spaces removed
        val compact = text.filter { it.isLetterOrDigit() }
        for (rule in RULES) {
            if (rule.excludes.any { containsWord(text, it) }) continue
            val hit = rule.keywords.firstOrNull { matchesKeyword(text, compact, it) } ?: continue
            return KeywordCategory(rule.category, rule.subcategory, hit)
        }
        return null
    }

    private fun matchesKeyword(text: String, compact: String, keyword: String): Boolean =
        if (keyword.contains(' ')) compact.contains(keyword.filter { it.isLetterOrDigit() }) else containsWord(text, keyword)

    @Synchronized
    private fun containsWord(text: String, word: String): Boolean =
        wordRegexes.getOrPut(word) { Regex("(?<![a-z0-9])${Regex.escape(word)}(?![a-z0-9])") }.containsMatchIn(text)
}
