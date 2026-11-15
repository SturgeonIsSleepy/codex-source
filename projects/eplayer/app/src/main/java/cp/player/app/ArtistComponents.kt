package cp.player.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException

@Composable
internal fun ArtistAvatar(image: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Person, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(image, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun ArtistChoiceRow(entry: MusicEntry, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(onClick = open).padding(horizontal = 20.dp, vertical = 12.dp)
        .testTag("artist-choice-${entry.id}"), verticalAlignment = Alignment.CenterVertically) {
        ArtistAvatar(entry.image, Modifier.size(56.dp).testTag("artist-choice-avatar-${entry.id}"))
        Text(entry.name, Modifier.weight(1f).padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Metadata is requested only while a row is visible; the repository reuses its bounded header cache. */
@Composable
internal fun ResolvedArtistChoice(entry: MusicEntry, open: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var profile by remember(entry.source, entry.id) { mutableStateOf(entry) }
    LaunchedEffect(entry.source, entry.id) {
        if (profile.image.isNullOrBlank()) try { profile = MusicRepository(context, entry.source).artistHeader(entry).entry }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep the named row and its avatar placeholder usable. */ }
    }
    ArtistChoiceRow(profile, open)
}

@Composable
internal fun ArtistProfileHeader(entry: MusicEntry, description: String) {
    var expanded by remember(entry.source, entry.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).testTag("artist-profile-header")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtistAvatar(entry.image, Modifier.size(72.dp).testTag("artist-header-avatar"))
            Text(entry.name, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.headlineSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (description.isNotBlank()) Text(description, Modifier.padding(top = 10.dp).clickable { expanded = true }, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (expanded) TravelAlertDialog(onDismissRequest = { expanded = false }, title = { Text(entry.name) },
        text = { Text(description, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("关闭") } })
}
