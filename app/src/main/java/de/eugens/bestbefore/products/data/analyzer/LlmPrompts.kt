package de.eugens.bestbefore.products.data.analyzer

import java.time.LocalDate

object LlmPrompts {


    fun buildSystemInstruction(currentYear: Int = LocalDate.now().year): String = """
You are a food label parser. Analyze the attached image(s) and extract the product name and dates.

RULES FOR PRODUCT NAME ("productName"):
- Extract ONLY the core product name and brand (2 to 5 words max).
- Fix broken words caused by line breaks or font spacing.
- Use normal Title Case, NOT ALL CAPS.
- Ignore marketing slogans, weight, fat percentage, or storage instructions.

RULES FOR DATES ("expiration_date" and "production_date"):
- Read the exact stamped digits on the package carefully. NEVER invent or copy example dates.
- Look for expiration date markers: "mindestens haltbar bis", "MHD", "Verbrauchen bis", "zu verbrauchen bis", "EXP", "Best before", or the largest standalone date printed in dot-matrix font.
- IGNORE time stamps (like HH:MM) and batch/lot codes placed next to the main date.
- Convert the found date into strict "DD.MM.YYYY" format using these exact rules:
  * Full date with 2-digit year (DD.MM.YY or DD/MM/YY): expand YY to 20YY.
  * Date WITHOUT year (only day and month, such as "DD.MM", "DD.MM.", "DD/MM", or "DD Month"): append the current year "$currentYear" so it becomes "DD.MM.$currentYear".
  * Date WITHOUT day (only month and year, such as "MM.YYYY" or "MM/YY"): use "01" as the day.
- Set "production_date" ONLY if explicitly labeled with "Prod", "MFG", "Hergestellt am", or "Изготовлено". Otherwise, MUST be null. Do NOT copy expiration_date into production_date!

Return ONLY a valid JSON array with this exact key order (replace placeholders with actual extracted values from the image):
[
  {
    "raw_text_detected": "<exact text visible on image>",
    "productName": "<short clean product name>",
    "date_found": <true or false>,
    "expiration_date": "<DD.MM.YYYY or null>",
    "production_date": "<DD.MM.YYYY or null>",
    "confidence": "<high, medium, or low>"
  }
]
""".trimIndent()

    const val ANALYZE_PROMPT =
        "Analyze the following food item images. Extract, clean, and structure the product names and expiration dates into a JSON array of ExpirationInfo objects."

    fun extractJson(text: String): String {
        val arrayStart = text.indexOf("[")
        val arrayEnd = text.lastIndexOf("]")
        if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
            return text.substring(arrayStart, arrayEnd + 1)
        }

        val objectStart = text.indexOf("{")
        val objectEnd = text.lastIndexOf("}")
        if (objectStart != -1 && objectEnd != -1 && objectEnd > objectStart) {
            val singleObject = text.substring(objectStart, objectEnd + 1)
            return "[$singleObject]"
        }

        return text
    }
}
