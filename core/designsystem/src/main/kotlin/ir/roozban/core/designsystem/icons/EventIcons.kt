package ir.roozban.core.designsystem.icons

import androidx.annotation.DrawableRes
import ir.roozban.core.designsystem.R
import ir.roozban.core.model.EventKind

@DrawableRes
fun eventIcon(kind: EventKind): Int = when (kind) {
    EventKind.BIRTHDAY -> R.drawable.ic_event_birthday
    EventKind.WEDDING -> R.drawable.ic_event_ring
    EventKind.ENGAGEMENT -> R.drawable.ic_event_heart
    EventKind.MEMORIAL -> R.drawable.ic_event_flower
    EventKind.OTHER -> R.drawable.ic_event_star
}

fun eventKindName(kind: EventKind): String = when (kind) {
    EventKind.BIRTHDAY -> "تولد"
    EventKind.WEDDING -> "سالگرد ازدواج"
    EventKind.ENGAGEMENT -> "سالگرد عقد"
    EventKind.MEMORIAL -> "یادبود"
    EventKind.OTHER -> "مناسبت"
}
