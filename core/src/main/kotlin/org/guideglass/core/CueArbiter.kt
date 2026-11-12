package org.guideglass.core

/** A cue the arbiter started playing, or cut off. */
data class PlayEvent(
    val cue: Cue,
    val startT: Double,
    val route: AudioRoute,
    /** Kind of the cue this one cut off, if any. */
    val interrupted: CueKind? = null,
)

/** What happened to a requested cue, for logs and repetition metrics. */
enum class Fate { PLAYED, DROPPED_DUPLICATE, DROPPED_STALE, DROPPED_RATE, DROPPED_BUSY, CUT_OFF }

data class CueRecord(val cue: Cue, val fate: Fate, val t: Double)

/**
 * Turns requested cues into one audio stream (REQ-15, 16, 17; docs/01 section 4).
 *  - A higher priority cuts off a lower one at once. Equal or lower priority queues.
 *  - A cue whose key is already playing or queued is dropped as a duplicate.
 *  - Queued cues are dropped when older than their staleness limit.
 *  - P3 speech is limited to one start per [CoreConfig.nonUrgentGapS]; P4 ticks never queue.
 */
class CueArbiter(private val cfg: CoreConfig) {
    private var playing: Cue? = null
    private var playingEnd = 0.0
    private var lastP3Start = Double.NEGATIVE_INFINITY
    private var lastStopStart = Double.NEGATIVE_INFINITY
    private val queue = mutableListOf<Cue>()
    val records = mutableListOf<CueRecord>()
    var route = AudioRoute.GLASSES

    val isBusy get() = playing != null

    fun submit(cue: Cue, now: Double): List<PlayEvent> {
        advance(now)
        val cur = playing
        if ((cur != null && cur.key == cue.key) || queue.any { it.key == cue.key }) {
            records += CueRecord(cue, Fate.DROPPED_DUPLICATE, now); return emptyList()
        }
        if (cue.priority == Priority.P4 && (cur != null || queue.isNotEmpty())) {
            records += CueRecord(cue, Fate.DROPPED_BUSY, now); return emptyList()
        }
        // A STOP shortly after another STOP adds nothing: the user is already stopping (S-19 crowd).
        if (cue.kind == CueKind.STOP && now - lastStopStart < cfg.stopRefractoryS) {
            records += CueRecord(cue, Fate.DROPPED_RATE, now); return emptyList()
        }
        // "Camera lost" and "Glasses disconnected" cut off everything except a STOP that is playing: hazard
        // cautions must not delay the news that the system can no longer see (review finding 16).
        val lost = cue.kind == CueKind.CAMERA_LOST || cue.kind == CueKind.GLASSES_LOST
        if (cur != null && (cue.priority.level < cur.priority.level || (lost && cur.kind != CueKind.STOP && cur.priority.level >= cue.priority.level))) {
            records += CueRecord(cur, Fate.CUT_OFF, now)
            // Lower-priority cues waiting behind are left in the queue; they expire by staleness.
            return listOf(start(cue, now, interrupted = cur.kind))
        }
        queue += cue
        return advance(now)
    }

    /** Call on every tick; finishes the current cue and starts the next one that is still fresh. */
    fun advance(now: Double): List<PlayEvent> {
        val out = mutableListOf<PlayEvent>()
        if (playing != null && now >= playingEnd) playing = null
        while (playing == null && queue.isNotEmpty()) {
            queue.sortWith(compareBy({ it.priority.level }, { if (it.kind == CueKind.CAMERA_LOST || it.kind == CueKind.GLASSES_LOST) 0 else 1 }, { it.requestedAt }))
            val next = queue.removeAt(0)
            when {
                now - next.requestedAt > next.staleAfterS -> records += CueRecord(next, Fate.DROPPED_STALE, now)
                next.priority == Priority.P3 && now - lastP3Start < cfg.nonUrgentGapS -> {
                    // Too soon after the last non-urgent utterance: put it back if it can still wait.
                    if (next.requestedAt + next.staleAfterS > lastP3Start + cfg.nonUrgentGapS) {
                        queue += next; break
                    } else records += CueRecord(next, Fate.DROPPED_RATE, now)
                }
                else -> out += start(next, now, null)
            }
        }
        return out
    }

    /** Remove queued and playing cues matching [pred] (e.g. cloud speech when a hazard appears). */
    fun cancel(now: Double, pred: (Cue) -> Boolean) {
        queue.removeAll { pred(it) }
        val cur = playing
        if (cur != null && pred(cur)) { records += CueRecord(cur, Fate.CUT_OFF, now); playing = null }
    }

    private fun start(cue: Cue, now: Double, interrupted: CueKind?): PlayEvent {
        playing = cue
        playingEnd = now + cue.durationS
        if (cue.priority == Priority.P3) lastP3Start = now
        if (cue.kind == CueKind.STOP) lastStopStart = now
        records += CueRecord(cue, Fate.PLAYED, now)
        return PlayEvent(cue, now, route, interrupted)
    }
}
