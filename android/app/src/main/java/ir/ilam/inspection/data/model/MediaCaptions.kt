package ir.ilam.inspection.data.model

/**
 * The visit form expects a fixed set of shots. Ten photo captions and six video
 * captions are standard, and the rest are described by the expert, so a report
 * from one county reads like a report from another.
 */
object MediaCaptions {

    const val MAX_PHOTOS = 30
    const val MAX_VIDEOS = 10

    fun limitFor(type: MediaType): Int =
        if (type == MediaType.VIDEO) MAX_VIDEOS else MAX_PHOTOS

    /** Standard captions still free, so the same one is not used twice. */
    fun available(standard: List<String>, used: Collection<String?>): List<String> {
        val taken = used.filterNotNull().toSet()
        return standard.filter { it !in taken }
    }
}
