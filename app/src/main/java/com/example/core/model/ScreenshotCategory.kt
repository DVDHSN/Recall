package com.example.core.model

enum class ScreenshotCategory(val displayName: String, val iconName: String) {
    CODE_ERROR("Code & Errors", "code"),
    RECIPES("Recipes & Food", "restaurant"),
    NETWORK_TECH("Network & Wi-Fi", "wifi"),
    STUDY_NOTES("Study & Notes", "menu_book"),
    FINANCE_RECEIPTS("Finance & Receipts", "receipt_long"),
    SHOPPING_PRICES("Shopping & Hardware", "shopping_cart"),
    COMMUNICATION("Messages & Chats", "chat"),
    GENERAL_REFERENCE("General Reference", "description");

    companion object {
        fun fromString(name: String): ScreenshotCategory? {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
}
