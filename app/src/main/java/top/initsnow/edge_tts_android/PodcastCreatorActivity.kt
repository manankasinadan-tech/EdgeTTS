package top.initsnow.edge_tts_android

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import top.initsnow.edge_tts_android.ui.theme.EdgeTtsTheme

class PodcastCreatorActivity : ComponentActivity() {

    private val viewModel: PodcastViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EdgeTtsTheme {
                PodcastCreatorScreen(
                    viewModel = viewModel,
                    onOpenSettings = {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastCreatorScreen(
    viewModel: PodcastViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val speakers by viewModel.speakers.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val historyPodcasts by viewModel.historyPodcasts.collectAsStateWithLifecycle()
    val rawScriptText by viewModel.rawScriptText.collectAsStateWithLifecycle()
    val currentStyle by viewModel.currentStyle.collectAsStateWithLifecycle()
    val previewPlayingSegmentId by viewModel.previewPlayingSegmentId.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isRawMode by remember { mutableStateOf(false) }
    var showAddSpeakerDialog by remember { mutableStateOf(false) }
    var showStyleBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("podcast_creator_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Edge TTS Studio",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val isNative = EdgeTtsNative.isReady()
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isNative) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = if (isNative) "Rust JNI" else "Direct WS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = if (isNative) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        Text(
                            text = "Créateur de podcasts multi-voix",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_system_tts_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Paramètres TTS Moteur"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("studio_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = "Dialogue") },
                    label = { Text("Dialogue") },
                    modifier = Modifier.testTag("nav_tab_dialogue")
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Default.Group, contentDescription = "Intervenants") },
                    label = { Text("Voix (${speakers.size}/5)") },
                    modifier = Modifier.testTag("nav_tab_speakers")
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    icon = { Icon(Icons.Default.Headphones, contentDescription = "Lecteur") },
                    label = { Text("Lecteur (${historyPodcasts.size})") },
                    modifier = Modifier.testTag("nav_tab_player")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {
                0 -> DialogueScriptTab(
                    speakers = speakers,
                    segments = segments,
                    currentStyle = currentStyle,
                    previewPlayingSegmentId = previewPlayingSegmentId,
                    isRawMode = isRawMode,
                    rawScriptText = rawScriptText,
                    onOpenStyleSelector = { showStyleBottomSheet = true },
                    onPreviewSegment = { viewModel.previewSegment(it) },
                    onToggleRawMode = { isRawMode = !isRawMode },
                    onRawScriptChange = { viewModel.setRawScriptText(it) },
                    onParseRawScript = {
                        viewModel.parseRawScriptText()
                        isRawMode = false
                    },
                    onAddSegment = { speakerId -> viewModel.addSegment(speakerId) },
                    onUpdateSegmentText = { id, text -> viewModel.updateSegmentText(id, text) },
                    onUpdateSegmentSpeaker = { id, spId -> viewModel.updateSegmentSpeaker(id, spId) },
                    onMoveSegment = { from, to -> viewModel.moveSegment(from, to) },
                    onDeleteSegment = { id -> viewModel.deleteSegment(id) },
                    onLoadSample = { viewModel.loadSampleDialog(it) },
                    onGeneratePodcast = { viewModel.generatePodcast() },
                    generationState = generationState,
                    onResetGeneration = { viewModel.resetGenerationState() }
                )

                1 -> SpeakersTab(
                    speakers = speakers,
                    availableVoices = availableVoices,
                    onAddSpeakerClick = { showAddSpeakerDialog = true },
                    onDeleteSpeaker = { viewModel.removeSpeaker(it) },
                    onUpdateSpeaker = { id, name, voice -> viewModel.updateSpeaker(id, name, voice) }
                )

                2 -> PlayerTab(
                    playerState = playerState,
                    history = historyPodcasts,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onSeek = { viewModel.seekTo(it) },
                    onPlayFile = { viewModel.playAudioFile(it) },
                    onShareFile = { viewModel.sharePodcast(context, it) },
                    onOpenFile = { viewModel.openPodcast(context, it) },
                    onDeleteFile = { viewModel.deleteHistoryFile(it) }
                )
            }

            // Generation Progress Modal / Floating Overlay
            AnimatedVisibility(
                visible = generationState is GenerationState.Progress,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                if (generationState is GenerationState.Progress) {
                    val prog = generationState as GenerationState.Progress
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = prog.message,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            val progressFloat = if (prog.totalSteps > 0) {
                                prog.currentStep.toFloat() / prog.totalSteps.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progressFloat },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStyleBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStyleBottomSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ConversationStylePicker(
                selected = currentStyle,
                onSelectStyle = {
                    viewModel.setStyle(it)
                    showStyleBottomSheet = false
                }
            )
        }
    }

    if (showAddSpeakerDialog) {
        AddSpeakerDialog(
            currentCount = speakers.size,
            availableVoices = availableVoices,
            onDismiss = { showAddSpeakerDialog = false },
            onConfirm = { name, voice ->
                viewModel.addSpeaker(name, voice)
                showAddSpeakerDialog = false
            }
        )
    }
}

@Composable
fun DialogueScriptTab(
    speakers: List<Speaker>,
    segments: List<ScriptSegment>,
    currentStyle: ConversationStyle,
    previewPlayingSegmentId: String?,
    isRawMode: Boolean,
    rawScriptText: String,
    onOpenStyleSelector: () -> Unit,
    onPreviewSegment: (String) -> Unit,
    onToggleRawMode: () -> Unit,
    onRawScriptChange: (String) -> Unit,
    onParseRawScript: () -> Unit,
    onAddSegment: (String) -> Unit,
    onUpdateSegmentText: (String, String) -> Unit,
    onUpdateSegmentSpeaker: (String, String) -> Unit,
    onMoveSegment: (Int, Int) -> Unit,
    onDeleteSegment: (String) -> Unit,
    onLoadSample: (Int) -> Unit,
    onGeneratePodcast: () -> Unit,
    generationState: GenerationState,
    onResetGeneration: () -> Unit
) {
    val speakerMap = remember(speakers) { speakers.associateBy { it.id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Conversation Style Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Style de conversation",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${currentStyle.description} • Pause ${currentStyle.pauseBetweenTurnsMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    // Style selector button
                    OutlinedButton(
                        onClick = onOpenStyleSelector,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("conversation_style_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentStyle.title,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mode switch row and Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onToggleRawMode,
                modifier = Modifier.testTag("toggle_raw_script_mode_button")
            ) {
                Icon(
                    imageVector = if (isRawMode) Icons.Default.ViewList else Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isRawMode) "Mode Segments" else "Mode Texte Balisé")
            }

            OutlinedButton(
                onClick = { onLoadSample(1) },
                modifier = Modifier.testTag("load_sample_dialogue_button")
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Exemple")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isRawMode) {
            // Raw Tagged Text Editor
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Script format [Intervenant] Réplique :",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = rawScriptText,
                        onValueChange = onRawScriptChange,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("raw_script_text_field"),
                        placeholder = {
                            Text("[Denise] Bonjour!\n[Henri] Salut, bienvenue dans notre émission!")
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onParseRawScript,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apply_raw_script_button")
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Appliquer au dialogue")
                    }
                }
            }
        } else {
            // Visual Segments List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(segments, key = { _, item -> item.id }) { index, segment ->
                    val currentSpeaker = speakerMap[segment.speakerId] ?: speakers.firstOrNull()
                    val isPreviewing = previewPlayingSegmentId == segment.id

                    SegmentCard(
                        segment = segment,
                        index = index,
                        totalSegments = segments.size,
                        speaker = currentSpeaker,
                        speakers = speakers,
                        isPreviewing = isPreviewing,
                        onPreview = { onPreviewSegment(segment.id) },
                        onTextChange = { onUpdateSegmentText(segment.id, it) },
                        onSpeakerSelect = { onUpdateSegmentSpeaker(segment.id, it) },
                        onMoveUp = { onMoveSegment(index, index - 1) },
                        onMoveDown = { onMoveSegment(index, index + 1) },
                        onDelete = { onDeleteSegment(segment.id) }
                    )
                }

                item {
                    // Add segment button
                    val defaultSpeakerId = speakers.firstOrNull()?.id ?: "sp_1"
                    OutlinedButton(
                        onClick = { onAddSegment(defaultSpeakerId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("add_segment_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ajouter une réplique")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Podcast Generation CTA
        Button(
            onClick = onGeneratePodcast,
            enabled = generationState !is GenerationState.Progress && segments.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("generate_podcast_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Générer Podcast Multi-voix (MP3)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        // Error message snack
        if (generationState is GenerationState.Error) {
            val err = generationState as GenerationState.Error
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = err.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onResetGeneration) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationStylePicker(
    selected: ConversationStyle,
    onSelectStyle: (ConversationStyle) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            text = "Style de la conversation",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Adapte le ton, les réactions humaines et le rythme des échanges",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        ConversationStyle.entries.forEach { style ->
            val isCurrent = selected == style
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectStyle(style) }
                    .then(
                        if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                        else Modifier
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCurrent,
                        onClick = { onSelectStyle(style) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = style.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = style.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Pause répliques : ${style.pauseBetweenTurnsMs}ms • Intonation : ${style.pitchMod}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SegmentCard(
    segment: ScriptSegment,
    index: Int,
    totalSegments: Int,
    speaker: Speaker?,
    speakers: List<Speaker>,
    isPreviewing: Boolean,
    onPreview: () -> Unit,
    onTextChange: (String) -> Unit,
    onSpeakerSelect: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedSpeakerMenu by remember { mutableStateOf(false) }
    val color = speaker?.colorHex?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("segment_card_$index"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Speaker selector and action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speaker Badge & selector
                Box {
                    Surface(
                        color = color.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable { expandedSpeakerMenu = true }
                            .testTag("segment_speaker_select_$index")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = speaker?.name ?: "Intervenant",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expandedSpeakerMenu,
                        onDismissRequest = { expandedSpeakerMenu = false }
                    ) {
                        speakers.forEach { sp ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(sp.colorHex))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("${sp.name} (${sp.voiceShortName})")
                                    }
                                },
                                onClick = {
                                    onSpeakerSelect(sp.id)
                                    expandedSpeakerMenu = false
                                }
                            )
                        }
                    }
                }

                // Actions: Preview audio, reorder & delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPreview,
                        modifier = Modifier.size(32.dp).testTag("preview_segment_$index")
                    ) {
                        if (isPreviewing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.PlayCircleOutline,
                                contentDescription = "Écouter la réplique",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (index > 0) {
                        IconButton(
                            onClick = onMoveUp,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Monter", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < totalSegments - 1) {
                        IconButton(
                            onClick = onMoveDown,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Descendre", modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Supprimer",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speech Text Input
            OutlinedTextField(
                value = segment.text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("segment_text_input_$index"),
                placeholder = { Text("Texte prononcé par ${speaker?.name ?: "l'intervenant"}...") },
                shape = RoundedCornerShape(8.dp),
                maxLines = 5
            )
        }
    }
}

@Composable
fun SpeakersTab(
    speakers: List<Speaker>,
    availableVoices: List<PodcastViewModel.VoiceOption>,
    onAddSpeakerClick: () -> Unit,
    onDeleteSpeaker: (String) -> Unit,
    onUpdateSpeaker: (String, String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Intervenants du dialogue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${speakers.size} sur 5 maximum",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (speakers.size < 5) {
                Button(
                    onClick = onAddSpeakerClick,
                    modifier = Modifier.testTag("add_speaker_button")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ajouter")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(speakers) { _, sp ->
                SpeakerCard(
                    speaker = sp,
                    canDelete = speakers.size > 1,
                    availableVoices = availableVoices,
                    onDelete = { onDeleteSpeaker(sp.id) },
                    onUpdate = { name, voice -> onUpdateSpeaker(sp.id, name, voice) }
                )
            }
        }
    }
}

@Composable
fun SpeakerCard(
    speaker: Speaker,
    canDelete: Boolean,
    availableVoices: List<PodcastViewModel.VoiceOption>,
    onDelete: () -> Unit,
    onUpdate: (String, String) -> Unit
) {
    var name by remember(speaker.name) { mutableStateOf(speaker.name) }
    var selectedVoice by remember(speaker.voiceShortName) { mutableStateOf(speaker.voiceShortName) }
    var expandedVoiceDropdown by remember { mutableStateOf(false) }

    val color = Color(speaker.colorHex)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onUpdate(it, selectedVoice)
                    },
                    label = { Text("Nom de l'intervenant") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Voice Selector Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expandedVoiceDropdown = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Voix : $selectedVoice",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }

                DropdownMenu(
                    expanded = expandedVoiceDropdown,
                    onDismissRequest = { expandedVoiceDropdown = false },
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    availableVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.displayName) },
                            onClick = {
                                selectedVoice = voice.shortName
                                onUpdate(name, voice.shortName)
                                expandedVoiceDropdown = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddSpeakerDialog(
    currentCount: Int,
    availableVoices: List<PodcastViewModel.VoiceOption>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("Intervenant ${currentCount + 1}") }
    var selectedVoice by remember {
        mutableStateOf(
            when (currentCount) {
                0 -> "fr-FR-DeniseNeural"
                1 -> "fr-FR-HenriNeural"
                2 -> "fr-FR-VivienneMultilingualNeural"
                3 -> "fr-FR-RemyMultilingualNeural"
                else -> "en-US-EmmaMultilingualNeural"
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un intervenant") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Voix Edge TTS :", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    itemsIndexed(availableVoices) { _, v ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVoice = v.shortName }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedVoice == v.shortName,
                                onClick = { selectedVoice = v.shortName }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(v.displayName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, selectedVoice) }) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun PlayerTab(
    playerState: PlayerUiState,
    history: List<GeneratedPodcast>,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayFile: (java.io.File) -> Unit,
    onShareFile: (java.io.File) -> Unit,
    onOpenFile: (java.io.File) -> Unit,
    onDeleteFile: (java.io.File) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Active Audio Player Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("audio_player_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playerState.activeTitle.ifEmpty { "Aucun fichier en cours" },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (playerState.activeFile != null) "/Music/EdgeTTSPodcasts/" else "Générez un dialogue pour l'écouter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Seek slider
                val totalMs = playerState.totalDurationMs.coerceAtLeast(1L)
                val currentMs = playerState.currentPositionMs.coerceIn(0L, totalMs)

                Slider(
                    value = currentMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..totalMs.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(currentMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        formatDuration(totalMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Play / Pause / Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    playerState.activeFile?.let { file ->
                        IconButton(
                            onClick = { onShareFile(file) },
                            modifier = Modifier.testTag("share_podcast_button")
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Partager",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = onTogglePlay,
                        enabled = playerState.activeFile != null,
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("toggle_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Lecture",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    playerState.activeFile?.let { file ->
                        IconButton(
                            onClick = { onOpenFile(file) },
                            modifier = Modifier.testTag("open_podcast_button")
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = "Ouvrir",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // History Section
        Text(
            text = "Podcasts générés (/Music/EdgeTTSPodcasts/)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aucun podcast exporté pour l'instant",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(history) { _, podcast ->
                    val isCurrent = playerState.activeFile?.absolutePath == podcast.file.absolutePath
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayFile(podcast.file) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onPlayFile(podcast.file) }) {
                                Icon(
                                    imageVector = if (isCurrent && playerState.isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = "Lire",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = podcast.name,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatDuration(podcast.durationMs)} • ${podcast.sizeBytes / 1024} KB • ${podcast.formattedDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onShareFile(podcast.file) }) {
                                Icon(Icons.Default.Share, contentDescription = "Partager", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onDeleteFile(podcast.file) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Supprimer",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
