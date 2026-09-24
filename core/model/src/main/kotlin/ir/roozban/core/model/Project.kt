package ir.roozban.core.model

import java.time.Instant

/** Colors are indices into a fixed palette so they adapt to light and dark themes. */
data class Project(
    val id: String,
    val name: String,
    val color: Int = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Label(
    val id: String,
    val name: String,
    val color: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Normalizes a user-typed name for matching: Arabic ي/ك, ZWNJ and case differences are ignored. */
fun normalizeName(name: String): String =
    name.trim()
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        .replace("‌", "").replace(" ", "")
        .lowercase()
