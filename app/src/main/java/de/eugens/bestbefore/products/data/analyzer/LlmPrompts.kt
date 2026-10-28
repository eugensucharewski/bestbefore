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

RULES FOR MULTIPLE ITEMS:
- The output JSON array MUST contain exactly one object for EACH item provided in the input.
- You MUST maintain the exact original order (Item 1 first, Item 2 second, etc.).
- Do NOT skip, combine, or group items, even if they are identical or unrecognizable.
- Include the "item_index" field in each object to ensure correct mapping.

Return ONLY a valid JSON array with this exact key order (replace placeholders with actual extracted values from the image):
[
  {
    "item_index": 1,
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

    const val NANO_SYSTEM_INSTRUCTION = """
You are a food label extractor. 
You will be provided with one or two images of the SAME product. One image may contain the product name (front label), and the other may contain the expiration date (back or bottom label).
Extract:
1. Brand and core product name (Title Case, 2-5 words).
2. The exact raw date strings visible (do NOT reformat them).

Respond strictly in this JSON format:
{
  "productName": "...",
  "expiration_date": "...",
  "production_date": "...",
  "date_found": true
}
"""
    const val NANO_ANALYZE_PROMPT = "Analyze the provided product image(s). Extract product name and expiration/production dates as JSON."

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
