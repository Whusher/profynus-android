@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.profynus.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.profynus.music.data.AppearancePreferences
import com.profynus.music.data.AppearanceSettings
import com.profynus.music.data.LocalMusicRepository
import com.profynus.music.data.LocalTrack
import com.profynus.music.data.PlayerVisual
import com.profynus.music.data.TrackDetails
import com.profynus.music.data.TrackDetailsStore
import com.profynus.music.data.details
import com.profynus.music.data.withDetails
import com.profynus.music.data.ShapeProfile
import com.profynus.music.data.SortDirection
import com.profynus.music.data.TrackSortField
import com.profynus.music.data.sortLocalTracks
import com.profynus.music.playback.PlaybackConnection
import com.profynus.music.playback.PlaybackSnapshot
import com.profynus.music.youtube.YouTubeBrowserDiagnosticScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private enum class Destination(val title: String, val icon: ImageVector) {
    PLAYER("Player", Icons.Default.MusicNote),
    LIBRARY("Library", Icons.Default.LibraryMusic),
    FEED("Feed", Icons.Default.Public),
    PROFILE("Profile", Icons.Default.AccountCircle),
    DOWNLOADER("Downloader", Icons.Default.Download),
}

/** Destinations that appear in the bottom bar (Downloader is drawer-only). */
private val BottomDestinations = Destination.entries.filter { it != Destination.DOWNLOADER }

@Composable
fun ProfynusApp() {
    val context = LocalContext.current
    val appearance = remember(context) { AppearancePreferences(context.applicationContext) }
    val settings by appearance.settings.collectAsState(initial = AppearanceSettings())
    val palette = ProfynusPalettes.all.firstOrNull { it.id == settings.paletteId }
        ?: ProfynusPalettes.all.first()
    val playback = remember(context) { PlaybackConnection(context.applicationContext) }
    val detailsStore = remember(context) { TrackDetailsStore(context.applicationContext) }
    val trackEdits by detailsStore.edits.collectAsState(initial = emptyMap())
    var editingDetails by rememberSaveable(stateSaver = TrackDetailsSaver) { mutableStateOf<TrackDetails?>(null) }
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(Destination.PLAYER) }
    val drawer = rememberDrawerState(initialValue = DrawerValue.Closed)

    DisposableEffect(playback) { onDispose { playback.release() } }
    // A position ticker is deliberately UI-scoped, not an audio-processing loop.
    LaunchedEffect(playback) {
        while (isActive) {
            if (playback.snapshot.queueSize > 0) playback.refresh()
            delay(550)
        }
    }

    ProfynusTheme(palette) {
        editingDetails?.let { details ->
            TrackDetailsEditor(details, detailsStore, onDismiss = { editingDetails = null })
        }
        val colors = MaterialTheme.colorScheme
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = colors.surfaceContainerLow) {
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 32.dp)) {
                        ProfynusLogo(size = 72.dp)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "PROFYNUS",
                            style = ProfynusWordmarkStyle.copy(brush = accentBrush()),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your music, your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Destination.entries.forEach { destination ->
                        NavigationDrawerItem(
                            label = { Text(destination.title) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            selected = page == destination,
                            onClick = {
                                page = destination
                                scope.launch { drawer.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = colors.primary.copy(alpha = 0.16f),
                                selectedIconColor = colors.primary,
                                selectedTextColor = colors.primary,
                                unselectedIconColor = colors.onSurfaceVariant,
                                unselectedTextColor = colors.onSurfaceVariant,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "LOCAL FIRST  •  YOUR MUSIC STAYS HERE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(28.dp),
                    )
                }
            },
        ) {
            AuroraBackground {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = colors.onBackground,
                    topBar = {
                        TopAppBar(
                            modifier = Modifier
                                .background(Brush.verticalGradient(listOf(
                                    colors.surfaceContainerHigh.copy(alpha = 0.92f),
                                    colors.surface.copy(alpha = 0.78f),
                                )))
                                .drawBehind {
                                    // Glass sheen and a fine illuminated edge; foreground stays crisp.
                                    drawRect(Brush.linearGradient(listOf(
                                        Color.White.copy(alpha = 0.07f),
                                        colors.primary.copy(alpha = 0.04f),
                                        Color.Transparent,
                                    )))
                                    drawLine(
                                        Brush.horizontalGradient(listOf(
                                            Color.Transparent,
                                            colors.primary.copy(alpha = 0.48f),
                                            Color.White.copy(alpha = 0.12f),
                                            Color.Transparent,
                                        )),
                                        Offset(0f, size.height),
                                        Offset(size.width, size.height),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                },
                            title = {
                                if (page == Destination.PLAYER) {
                                    ProfynusWordmark(logoSize = 28.dp)
                                } else {
                                    Text(page.title, style = MaterialTheme.typography.titleLarge)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawer.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                                }
                            },
                            actions = {
                                Box(
                                    Modifier
                                        .padding(end = 16.dp)
                                        .border(1.dp, colors.primary.copy(alpha = 0.4f), CircleShape)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        "LOCAL FIRST",
                                        color = colors.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent,
                                navigationIconContentColor = colors.onBackground,
                                titleContentColor = colors.onBackground,
                                actionIconContentColor = colors.primary,
                            ),
                        )
                    },
                    bottomBar = {
                        Column(Modifier.navigationBarsPadding().padding(bottom = 6.dp)) {
                            if (page != Destination.PLAYER && playback.snapshot.queueSize > 0) {
                                MiniPlayer(playback.snapshot, playback, onOpen = { page = Destination.PLAYER })
                            }
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                FloatingNavBar(
                                    items = BottomDestinations.map { NavItem(it.title, it.icon) },
                                    selectedIndex = BottomDestinations.indexOf(page),
                                    onSelect = { page = BottomDestinations[it] },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding)) {
                        Crossfade(targetState = page, label = "page") { target ->
                            when (target) {
                                Destination.PLAYER -> PlayerScreen(
                                    state = playback.snapshot,
                                    playback = playback,
                                    settings = settings,
                                    palette = palette,
                                    onOpenLibrary = { page = Destination.LIBRARY },
                                    onVisualChange = { visual -> scope.launch { appearance.selectPlayerVisual(visual) } },
                                    onEdit = { editingDetails = it },
                                )
                                Destination.LIBRARY -> LibraryScreen(
                                    playback = playback,
                                    edits = trackEdits,
                                    onEdit = { editingDetails = it },
                                    onTrackStarted = { page = Destination.PLAYER },
                                )
                                Destination.FEED -> FeedScreen()
                                Destination.PROFILE -> ProfileScreen(settings, palette, appearance)
                                Destination.DOWNLOADER -> YouTubeBrowserDiagnosticScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Player
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlayerScreen(
    state: PlaybackSnapshot,
    playback: PlaybackConnection,
    settings: AppearanceSettings,
    palette: ProfynusPalette,
    onOpenLibrary: () -> Unit,
    onVisualChange: (PlayerVisual) -> Unit,
    onEdit: (TrackDetails) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val hasQueue = state.queueSize > 0
    val heroShape = RoundedCornerShape(32.dp)
    // PlayerScreen, after `val heroShape = ...`
    val haptic = LocalHapticFeedback.current
    fun tick() = haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Hero: the globe sits in a glowing, gradient-bordered stage.
        Box(
            Modifier
                .fillMaxWidth()
                .swipeToSkip(enabled = hasQueue, onPrevious = { tick(); playback.previous() }, onNext = { tick(); playback.next() })
                .heightIn(min = 280.dp)
                .clip(heroShape)
                .background(Brush.verticalGradient(listOf(colors.surfaceContainerHigh, colors.surfaceContainerLowest)))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.45f), colors.outlineVariant)),
                    heroShape,
                )
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(colors.primary.copy(alpha = 0.18f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.45f),
                            radius = size.width * 0.7f,
                        ),
                    )
                }
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (settings.playerVisual == PlayerVisual.SPHERE) {
                GlobeVisualizer(palette.accent, settings.shape, settings.reducedMotion || !state.isPlaying, audioReactive = state.isPlaying)
            } else {
                TrackArtwork(
                    artworkUri = state.details?.artworkUri,
                    description = "Album art for ${state.title}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
                    fallback = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp), tint = colors.primary)
                            Text("No album art", color = colors.onSurfaceVariant)
                            if (state.details != null) {
                                Button(onClick = { onEdit(state.details) }) { Text("Add image") }
                            }
                        }
                    },
                )
            }
            if (settings.playerVisual == PlayerVisual.SPHERE) Text(
                "SONIC ATMOSPHERE",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            PlayerVisual.entries.forEach { visual ->
                FilterChip(
                    selected = settings.playerVisual == visual,
                    onClick = { onVisualChange(visual) },
                    label = { Text(visual.label) },
                    shape = CircleShape,
                    colors = profynusChipColors(),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel(
                    when {
                        state.isPlaying -> "Now playing"
                        hasQueue -> "Paused"
                        else -> "Ready"
                    },
                )
                state.details?.let { details ->
                    IconButton(onClick = { onEdit(details) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit song details")
                    }
                }
            }
            Text(
                state.title.ifBlank { "Your music starts here" },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
            Text(
                state.artist.ifBlank { if (hasQueue) "Unknown artist" else "Choose a song from your library" },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        state.details?.album?.takeIf { it.isNotBlank() }?.let { album ->
            Text(album, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }

        Column {
            TrackSeekBar(state, playback)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(
                    icon = Icons.Default.SkipPrevious,
                    description = "Previous track",
                    enabled = hasQueue,
                    onClick = { tick(); playback.previous() }
                )
                PlayPauseButton(isPlaying = state.isPlaying, enabled = hasQueue, onClick = playback::toggle)
                TransportButton(
                    icon = Icons.Default.SkipNext,
                    description = "Next track",
                    enabled = hasQueue,
                    onClick = { tick(); playback.next() }
                )
            }
        }

        if (!hasQueue) {
            Button(
                onClick = onOpenLibrary,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Browse your library")
            }
        }

        if (!playback.isConnected) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    "Connecting to local playback service…",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Panel(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), spacing = 4.dp) {
            SectionLabel(if (settings.playerVisual == PlayerVisual.SPHERE) "Shape profile  •  ${settings.shape.label}" else "Album artwork")
            Text(
                if (settings.playerVisual == PlayerVisual.SPHERE) "Your sphere reacts to the song’s decoded audio and uses the style selected in Profile."
                else "Make it yours. Add a cover using Edit song details.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TrackSeekBar(state: PlaybackSnapshot, playback: PlaybackConnection) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val progress = dragging ?: if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f
    SeekBar(
        progress = progress,
        enabled = state.durationMs > 0,
        onSeekPreview = { dragging = it },
        onSeekFinished = {
            playback.seekTo((it * state.durationMs).toLong())
            dragging = null
        },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            durationText((progress * state.durationMs).toLong()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            durationText(state.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniPlayer(state: PlaybackSnapshot, playback: PlaybackConnection, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 8.dp,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArtwork(
                    artworkUri = state.details?.artworkUri,
                    description = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                    maxSize = 160,
                    fallback = { ArtTile(seed = state.title, size = 44.dp, active = state.isPlaying) },
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        state.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        state.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                IconButton(onClick = playback::toggle) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = colors.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(onClick = playback::next) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.onSurface.copy(alpha = 0.08f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(accentBrush()))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Library
// ---------------------------------------------------------------------------------------------

@Composable
private fun LibraryScreen(
    playback: PlaybackConnection,
    edits: Map<String, TrackDetails>,
    onEdit: (TrackDetails) -> Unit,
    onTrackStarted: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val repository = remember(context) { LocalMusicRepository(context.applicationContext) }
    val requiredPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED)
    }
    var refreshKey by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf(emptyList<LocalTrack>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var showPlaylists by rememberSaveable { mutableStateOf(false) }
    var sortField by rememberSaveable { mutableStateOf(TrackSortField.NAME) }
    var sortDirection by rememberSaveable { mutableStateOf(SortDirection.ASCENDING) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (it) refreshKey++
    }
    LaunchedEffect(granted, refreshKey) {
        if (!granted) return@LaunchedEffect
        busy = true
        error = null
        try {
            tracks = repository.loadTracks()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error = "Unable to read local music: ${exception.localizedMessage.orEmpty()}"
        } finally {
            busy = false
        }
    }
    val displayedTracks = remember(tracks, edits, query, sortField, sortDirection) {
        val matching = tracks.map { it.withDetails(edits[it.uri.toString()]) }.filter { track ->
            track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true) ||
                    track.album.contains(query, ignoreCase = true)
        }
        sortLocalTracks(matching, sortField, sortDirection)
    }
    LaunchedEffect(sortField, sortDirection, query) { listState.scrollToItem(0) }
    if (showPlaylists) PlaylistsDialog(
        tracks = tracks.map { it.withDetails(edits[it.uri.toString()]) },
        canPlay = playback.isConnected,
        onPlay = { playback.play(it, 0); onTrackStarted() },
        onDismiss = { showPlaylists = false },
    )
    // Only rows that read this value recompose when the playing track changes.
    val nowPlaying by remember(playback) {
        derivedStateOf { playback.snapshot.mediaId }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Library", style = MaterialTheme.typography.displaySmall)
                Text(
                    "${tracks.size} local tracks • over ${LocalMusicRepository.MINIMUM_TRACK_DURATION_MS / 1000} seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = { refreshKey++ }, enabled = granted) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh music library")
            }
        }
        Spacer(Modifier.height(14.dp))

        if (!granted) {
            EmptyState(
                icon = Icons.Default.LibraryMusic,
                title = "Find the music on your phone",
                body = "Allow Profynus to read audio files stored on this device. Nothing leaves your phone.",
                action = {
                    Button(onClick = { launcher.launch(requiredPermission) }, shape = CircleShape) {
                        Text("Allow audio access")
                    }
                },
            )
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs, artists, albums") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainer,
                unfocusedContainerColor = colors.surfaceContainer,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outlineVariant,
                focusedLeadingIconColor = colors.primary,
                cursorColor = colors.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        androidx.compose.material3.TextButton(onClick = { showPlaylists = true }, enabled = !busy) { Text("Your playlists") }

        // Tap a chip to sort by it; tap the selected chip again to reverse the order.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(TrackSortField.entries.toList()) { field ->
                val selected = field == sortField
                val directionIcon: (@Composable () -> Unit)? = if (selected) {
                    {
                        val ascending = sortDirection == SortDirection.ASCENDING
                        Icon(
                            imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (ascending) "Ascending; tap to reverse" else "Descending; tap to reverse",
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (selected) {
                            sortDirection = if (sortDirection == SortDirection.ASCENDING) {
                                SortDirection.DESCENDING
                            } else {
                                SortDirection.ASCENDING
                            }
                        } else {
                            sortField = field
                        }
                    },
                    label = { Text(field.label) },
                    trailingIcon = directionIcon,
                    shape = CircleShape,
                    colors = profynusChipColors(),
                )
            }
        }

        if (!playback.isConnected) {
            Text(
                "Connecting to local playback service…",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        error?.let { Text(it, color = colors.error, modifier = Modifier.padding(bottom = 8.dp)) }

        when {
            busy -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            displayedTracks.isEmpty() -> EmptyState(
                icon = Icons.Default.LibraryMusic,
                title = if (tracks.isEmpty()) "No music found yet" else "No matches",
                body = if (tracks.isEmpty()) {
                    "Copy music onto your phone, then tap refresh."
                } else {
                    "Try a different search."
                },
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(displayedTracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id.toString() == nowPlaying,
                        showModified = sortField == TrackSortField.DATE_MODIFIED,
                        enabled = playback.isConnected,
                        onEdit = { onEdit(track.details()) },
                        onClick = {
                            playback.play(displayedTracks, index)
                            onTrackStarted()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: LocalTrack,
    isCurrent: Boolean,
    showModified: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (isCurrent) colors.primary.copy(alpha = 0.10f) else Color.Transparent,
        label = "rowBackground",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(
            artworkUri = track.artworkUri?.toString(),
            description = null,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)),
            maxSize = 160,
            fallback = { ArtTile(seed = track.title, size = 50.dp, active = isCurrent) },
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) colors.primary else colors.onSurface,
            )
            Text(
                track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (showModified) {
                Text(
                    "Modified ${modifiedDateText(track.modifiedAtEpochSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Text(
            durationText(track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit details for ${track.title}", modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Feed
// ---------------------------------------------------------------------------------------------

@Composable
private fun FeedScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Default.Public,
            title = "Feed",
            body = "Your future connection to Profynus public services.\nNot connected. No API requests are made from this section.",
            hero = { ProfynusLogo(size = 96.dp) },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------------------------

@Composable
private fun ProfileScreen(
    settings: AppearanceSettings,
    currentPalette: ProfynusPalette,
    appearance: AppearancePreferences,
) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfynusLogo(size = 64.dp)
            Column {
                Text("Your Profynus", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Make every listening session yours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Panel {
            SectionLabel("Shape profile")
            ShapeProfile.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { shape ->
                        FilterChip(
                            selected = shape == settings.shape,
                            onClick = { scope.launch { appearance.selectShape(shape) } },
                            label = {
                                Text(shape.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            },
                            shape = CircleShape,
                            colors = profynusChipColors(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Panel(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reduced motion", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Freeze the globe animation to save resources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.reducedMotion,
                    onCheckedChange = { scope.launch { appearance.setReducedMotion(it) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = colors.primary,
                        checkedBorderColor = colors.primary,
                        uncheckedThumbColor = colors.onSurfaceVariant,
                        uncheckedTrackColor = colors.surfaceContainerHighest,
                        uncheckedBorderColor = colors.outline,
                    ),
                )
            }
        }

        Panel {
            SectionLabel("Color your world")
            Text(
                "${currentPalette.title} • ${ProfynusPalettes.all.size} presets, saved automatically",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            ProfynusPalettes.all.chunked(4).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { item ->
                        PaletteSwatch(
                            palette = item,
                            selected = item.id == currentPalette.id,
                            modifier = Modifier.weight(1f),
                            onClick = { scope.launch { appearance.selectPalette(item.id) } },
                        )
                    }
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Text(
            "Local profile only. Account and cloud synchronization are not connected.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PaletteSwatch(
    palette: ProfynusPalette,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // No per-swatch label: Sora is wide and the name would not fit. The selected name is shown in
    // the panel subtitle, and screen readers get it through contentDescription.
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = palette.title }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1f)
                .border(2.dp, if (selected) palette.accent else Color.Transparent, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(palette.accent, palette.secondary))),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = palette.accent.readableOn(),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

private fun durationText(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}

/** MediaStore reports DATE_MODIFIED in epoch seconds. */
private fun modifiedDateText(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "Unknown"
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))
}
