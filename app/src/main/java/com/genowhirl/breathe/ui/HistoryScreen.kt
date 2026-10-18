package com.genowhirl.breathe.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.genowhirl.breathe.R
import com.genowhirl.breathe.model.HistoryTotals
import com.genowhirl.breathe.model.SessionCsv
import com.genowhirl.breathe.model.SessionRecord
import com.genowhirl.breathe.model.summary
import com.genowhirl.breathe.model.totals
import com.genowhirl.breathe.ui.theme.PhaseColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(
    records: List<SessionRecord>,
    onDelete: (SessionRecord) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SessionRecord?>(null) }

    // Lets the person pick where to save the file (Downloads, Drive, ...), no permission needed.
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(SessionCsv.format(records).toByteArray()) }
        }.isSuccess
        Toast.makeText(
            context,
            context.getString(if (ok) R.string.history_exported else R.string.history_export_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(
                enabled = records.isNotEmpty(),
                onClick = { export.launch("breathe-sessions-${LocalDate.now()}.csv") },
            ) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = stringResource(R.string.history_export))
            }
        }

        if (records.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Column
        }

        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TotalsCard(stringResource(R.string.history_last_7_days), records.totals(weekAgo), Modifier.weight(1f))
                    TotalsCard(stringResource(R.string.history_all_time), records.totals(), Modifier.weight(1f))
                }
            }
            items(records, key = { it.startedAt }) { record ->
                SessionRow(record, onLongPress = { deleting = record })
            }
            item {
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.history_clear), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_text)) },
            confirmButton = {
                TextButton(onClick = { onClear(); confirmClear = false }) { Text(stringResource(R.string.history_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(formatDateTime(record.startedAt)) },
            confirmButton = {
                TextButton(onClick = { onDelete(record); deleting = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TotalsCard(title: String, totals: HistoryTotals, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(formatDuration(totals.activeMillis), style = MaterialTheme.typography.headlineSmall)
            Text(
                plural(R.plurals.sessions_count, totals.sessions) + " · " +
                    plural(R.plurals.cycles_count, totals.cycles),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(record: SessionRecord, onLongPress: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (record.completed) PhaseColors.inhale else PhaseColors.holdOut),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(formatDateTime(record.startedAt), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${record.presetName} · ${record.pattern.summary()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatClock(record.activeMillis), style = MaterialTheme.typography.titleMedium)
                Text(
                    plural(R.plurals.cycles_count, record.cycles) +
                        if (record.completed) "" else " · " + stringResource(R.string.history_stopped),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun plural(id: Int, count: Int): String = LocalContext.current.resources.getQuantityString(id, count, count)

private val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatDateTime(epochMs: Long): String =
    dateTimeFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

/** 1 h 05 min, 12 min, 45 s. */
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return when {
        h > 0 -> "%d h %02d min".format(h, m)
        m > 0 -> "%d min".format(m)
        else -> "%d s".format(totalSeconds)
    }
}
