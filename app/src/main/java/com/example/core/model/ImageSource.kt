package com.example.core.model

enum class ImageSource(val displayName: String) {
    SCREENSHOT("Screenshots"),
    CAMERA("Camera & Photos"),
    DOWNLOAD("Downloads"),
    SOCIAL_MESSAGING("Social & Chats"),
    DOCUMENTS_NOTES("Documents & Notes"),
    IMPORTED("Imported"),
    OTHER("Other Images");

    companion object {
        fun fromString(value: String): ImageSource =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}
