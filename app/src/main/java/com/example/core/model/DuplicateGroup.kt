package com.example.core.model

data class DuplicateGroup(
    val groupId: String,
    val isExactDuplicate: Boolean,
    val representative: ScreenshotItem,
    val duplicates: List<ScreenshotItem>,
    val similarityDescription: String
)
