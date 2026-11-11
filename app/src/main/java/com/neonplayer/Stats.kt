package com.neonplayer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.TimeZone

class StatRow(val trackId: Long, val ms: Long, val plays: Int)

fun epochDay(now: Long = System.currentTimeMillis()): Long =
    (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L

/** period : 0 = semaine, 1 = mois, 2 = année, 3 = tout */
fun statRows(period: Int, all: List<Stat>, days: List<DayStat>): List<StatRow> {
    if (period == 3) return all.map { StatRow(it.trackId, it.listenMs, it.plays) }
    val n = when (period) { 0 -> 7; 1 -> 30; else -> 365 }
    val from = epochDay() - (n - 1)
    return days.filter { it.day >= from }.groupBy { it.trackId }
        .map { (id, l) -> StatRow(id, l.sumOf { it.listenMs }, l.sumOf { it.plays }) }
}

class RankedTrack(val t: TrackUi, val ms: Long, val plays: Int)
class RankedName(val name: String, val ms: Long, val plays: Int)
class StatsSummary(
    val totalMs: Long, val totalPlays: Int, val topTracks: List<RankedTrack>,
    val topArtist: RankedName?, val topAlbum: RankedName?
)

fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        h > 0 -> "$h h $m min"
        m > 0 -> "$m min $sec s"
        else -> "$sec s"
    }
}

fun buildStats(stats: List<StatRow>, tracks: List<TrackUi>): StatsSummary {
    val by = tracks.associateBy { it.id }
    val rt = stats.mapNotNull { s -> by[s.trackId]?.let { RankedTrack(it, s.ms, s.plays) } }
        .filter { it.ms > 0 || it.plays > 0 }
    val topTracks = rt.sortedWith(compareByDescending<RankedTrack> { it.plays }.thenByDescending { it.ms }).take(5)

    val aMs = HashMap<Long, Long>(); val aPl = HashMap<Long, Int>(); val aObj = HashMap<Long, Artist>()
    val bMs = HashMap<Long, Long>(); val bPl = HashMap<Long, Int>(); val bObj = HashMap<Long, Album>()
    for (r in rt) {
        for (a in r.t.artists) {
            aObj[a.id] = a
            aMs[a.id] = (aMs[a.id] ?: 0L) + r.ms
            aPl[a.id] = (aPl[a.id] ?: 0) + r.plays
        }
        r.t.album?.let { al ->
            bObj[al.id] = al
            bMs[al.id] = (bMs[al.id] ?: 0L) + r.ms
            bPl[al.id] = (bPl[al.id] ?: 0) + r.plays
        }
    }
    val topArtist = aObj.values.maxWithOrNull(compareBy<Artist>({ aMs[it.id] ?: 0L }, { aPl[it.id] ?: 0 }))
        ?.let { RankedName(it.name, aMs[it.id] ?: 0L, aPl[it.id] ?: 0) }
    val topAlbum = bObj.values.maxWithOrNull(compareBy<Album>({ bMs[it.id] ?: 0L }, { bPl[it.id] ?: 0 }))
        ?.let { RankedName(it.name, bMs[it.id] ?: 0L, bPl[it.id] ?: 0) }

    return StatsSummary(stats.sumOf { it.ms }, stats.sumOf { it.plays }, topTracks, topArtist, topAlbum)
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextLo, modifier = Modifier.weight(1f))
        Text(
            value, fontWeight = FontWeight.Medium, textAlign = TextAlign.End,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.3f)
        )
    }
}

@Composable
fun StatsCard(s: StatsSummary) {
    Column(Modifier.fillMaxWidth().neonBorder().padding(16.dp)) {
        if (s.totalMs == 0L) {
            Text("Écoutez de la musique pour voir apparaître vos statistiques.", color = TextLo)
            return@Column
        }
        StatLine("Temps d'écoute total", fmtDuration(s.totalMs))
        StatLine("Lectures", s.totalPlays.toString())
        StatLine("Son le plus écouté", s.topTracks.firstOrNull()?.let { "${it.t.title} (${it.plays}×)" } ?: "—")
        StatLine("Artiste le plus écouté", s.topArtist?.let { "${it.name} · ${fmtDuration(it.ms)}" } ?: "—")
        StatLine("Album le plus écouté", s.topAlbum?.let { "${it.name} · ${fmtDuration(it.ms)}" } ?: "—")
        if (s.topTracks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Top titres", color = Blue, fontWeight = FontWeight.Bold)
            s.topTracks.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = Violet, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    Cover(r.t.cover, Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            r.t.artistText.ifBlank { "Artiste inconnu" }, color = TextLo,
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${r.plays}×", color = Violet, fontWeight = FontWeight.Bold)
                        Text(fmtDuration(r.ms), color = TextLo, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
