package top.initsnow.edge_tts_android

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Speaker(
    val id: String,
    val name: String,
    val voiceShortName: String,
    val colorHex: Long
)

data class ScriptSegment(
    val id: String = UUID.randomUUID().toString(),
    val speakerId: String,
    val text: String
)

data class GeneratedPodcast(
    val file: File,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val formattedDate: String
)

sealed interface GenerationState {
    data object Idle : GenerationState
    data class Progress(
        val currentStep: Int,
        val totalSteps: Int,
        val speakerName: String,
        val message: String
    ) : GenerationState
    data class Success(val podcast: GeneratedPodcast) : GenerationState
    data class Error(val message: String) : GenerationState
}

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val activeFile: File? = null,
    val activeTitle: String = ""
)

class PodcastViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("podcast_studio_prefs", Context.MODE_PRIVATE)

    private val _speakers = MutableStateFlow<List<Speaker>>(emptyList())
    val speakers: StateFlow<List<Speaker>> = _speakers.asStateFlow()

    private val _segments = MutableStateFlow<List<ScriptSegment>>(emptyList())
    val segments: StateFlow<List<ScriptSegment>> = _segments.asStateFlow()

    private val _rawScriptText = MutableStateFlow("")
    val rawScriptText: StateFlow<String> = _rawScriptText.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private val _historyPodcasts = MutableStateFlow<List<GeneratedPodcast>>(emptyList())
    val historyPodcasts: StateFlow<List<GeneratedPodcast>> = _historyPodcasts.asStateFlow()

    private val _currentStyle = MutableStateFlow(loadSavedStyle())
    val currentStyle: StateFlow<ConversationStyle> = _currentStyle.asStateFlow()

    private val _previewPlayingSegmentId = MutableStateFlow<String?>(null)
    val previewPlayingSegmentId: StateFlow<String?> = _previewPlayingSegmentId.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackProgressJob: Job? = null

    private fun loadSavedStyle(): ConversationStyle {
        val name = prefs.getString("saved_conversation_style", ConversationStyle.CHALEUREUX.name)
        return try {
            ConversationStyle.valueOf(name ?: ConversationStyle.CHALEUREUX.name)
        } catch (e: Exception) {
            ConversationStyle.CHALEUREUX
        }
    }

    fun setStyle(style: ConversationStyle) {
        _currentStyle.value = style
        prefs.edit().putString("saved_conversation_style", style.name).apply()
    }

    fun previewSegment(segmentId: String) {
        val segment = _segments.value.firstOrNull { it.id == segmentId } ?: return
        if (segment.text.isBlank()) return

        val speaker = _speakers.value.firstOrNull { it.id == segment.speakerId } ?: _speakers.value.firstOrNull() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _previewPlayingSegmentId.value = segmentId
            try {
                val mp3Bytes = EdgeTtsNative.synthesizeMp3(
                    segment.text,
                    speaker.voiceShortName,
                    _currentStyle.value.rateMod,
                    "+0%",
                    _currentStyle.value.pitchMod
                )
                if (mp3Bytes != null && mp3Bytes.isNotEmpty()) {
                    val tempFile = File.createTempFile("preview_", ".mp3", getApplication<Application>().cacheDir)
                    tempFile.writeBytes(mp3Bytes)
                    withContext(Dispatchers.Main) {
                        playAudioFile(tempFile)
                    }
                }
            } catch (e: Exception) {
                Log.e("PodcastViewModel", "Preview error: ${e.message}")
            } finally {
                _previewPlayingSegmentId.value = null
            }
        }
    }

    data class VoiceOption(
        val shortName: String,
        val displayName: String,
        val locale: String
    )

    init {
        loadAvailableVoices()
        loadSpeakers()
        loadSegments()
        loadHistory()
    }

    private fun loadAvailableVoices() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<VoiceOption>()
            // Built-in popular defaults
            val popularDefaults = listOf(
                VoiceOption("fr-FR-DeniseNeural", "Denise (Français - Naturel)", "fr-FR"),
                VoiceOption("fr-FR-HenriNeural", "Henri (Français - Naturel)", "fr-FR"),
                VoiceOption("fr-FR-VivienneMultilingualNeural", "Vivienne (Français - Multilingue)", "fr-FR"),
                VoiceOption("fr-FR-RemyMultilingualNeural", "Remy (Français - Multilingue)", "fr-FR"),
                VoiceOption("fr-FR-EloiseNeural", "Eloise (Français - Doux)", "fr-FR"),
                VoiceOption("en-US-EmmaMultilingualNeural", "Emma (English US)", "en-US"),
                VoiceOption("en-US-BrianMultilingualNeural", "Brian (English US)", "en-US"),
                VoiceOption("en-US-GuyNeural", "Guy (English US)", "en-US"),
                VoiceOption("en-US-JennyNeural", "Jenny (English US)", "en-US"),
                VoiceOption("es-ES-AlvaroNeural", "Alvaro (Español)", "es-ES"),
                VoiceOption("es-ES-ElviraNeural", "Elvira (Español)", "es-ES"),
                VoiceOption("de-DE-ConradNeural", "Conrad (Deutsch)", "de-DE"),
                VoiceOption("it-IT-DiegoNeural", "Diego (Italiano)", "it-IT")
            )
            list.addAll(popularDefaults)

            // Attempt to enrich from repository cache if available
            try {
                val repo = VoiceRepository.getInstance(getApplication())
                val cached = repo.cachedVoices
                for (v in cached) {
                    if (list.none { it.shortName == v.shortName }) {
                        list.add(VoiceOption(v.shortName, v.displayName, v.localeTag))
                    }
                }
            } catch (ignored: Exception) {}

            _availableVoices.value = list
        }
    }

    private fun loadSpeakers() {
        val json = prefs.getString("saved_speakers", null)
        val list = mutableListOf<Speaker>()
        if (json != null) {
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        Speaker(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            voiceShortName = obj.getString("voiceShortName"),
                            colorHex = obj.optLong("colorHex", 0xFF2A9D8F)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("PodcastViewModel", "Error loading speakers: ${e.message}")
            }
        }

        if (list.isEmpty()) {
            list.add(Speaker("sp_1", "Denise", "fr-FR-DeniseNeural", 0xFF2A9D8F))
            list.add(Speaker("sp_2", "Henri", "fr-FR-HenriNeural", 0xFFE76F51))
        }

        _speakers.value = list
        saveSpeakersInternal(list)
    }

    private fun saveSpeakersInternal(list: List<Speaker>) {
        try {
            val array = JSONArray()
            for (s in list) {
                val obj = JSONObject()
                obj.put("id", s.id)
                obj.put("name", s.name)
                obj.put("voiceShortName", s.voiceShortName)
                obj.put("colorHex", s.colorHex)
                array.put(obj)
            }
            prefs.edit().putString("saved_speakers", array.toString()).apply()
        } catch (e: Exception) {
            Log.e("PodcastViewModel", "Error saving speakers: ${e.message}")
        }
    }

    fun addSpeaker(name: String, voiceShortName: String) {
        val current = _speakers.value
        if (current.size >= 5) return

        val colorPalette = listOf(
            0xFF2A9D8F, 0xFFE76F51, 0xFFE9C46A, 0xFF457B9D, 0xFF9B5DE5
        )
        val newColor = colorPalette[current.size % colorPalette.size]
        val newSpeaker = Speaker(
            id = "sp_${System.currentTimeMillis()}",
            name = name.ifBlank { "Intervenant ${current.size + 1}" },
            voiceShortName = voiceShortName,
            colorHex = newColor
        )
        val updated = current + newSpeaker
        _speakers.value = updated
        saveSpeakersInternal(updated)
    }

    fun removeSpeaker(speakerId: String) {
        val current = _speakers.value
        if (current.size <= 1) return
        val updated = current.filterNot { it.id == speakerId }
        _speakers.value = updated
        saveSpeakersInternal(updated)

        // Reassign affected segments to first remaining speaker
        val firstSpeaker = updated.firstOrNull() ?: return
        _segments.update { segs ->
            segs.map { if (it.speakerId == speakerId) it.copy(speakerId = firstSpeaker.id) else it }
        }
        syncRawTextFromSegments()
    }

    fun updateSpeaker(speakerId: String, newName: String, newVoice: String) {
        val current = _speakers.value
        val updated = current.map {
            if (it.id == speakerId) it.copy(name = newName, voiceShortName = newVoice) else it
        }
        _speakers.value = updated
        saveSpeakersInternal(updated)
        syncRawTextFromSegments()
    }

    private fun loadSegments() {
        val json = prefs.getString("saved_segments", null)
        val list = mutableListOf<ScriptSegment>()
        if (json != null) {
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ScriptSegment(
                            id = obj.getString("id"),
                            speakerId = obj.getString("speakerId"),
                            text = obj.getString("text")
                        )
                    )
                }
            } catch (ignored: Exception) {}
        }

        if (list.isEmpty()) {
            val sp1 = _speakers.value.getOrNull(0)?.id ?: "sp_1"
            val sp2 = _speakers.value.getOrNull(1)?.id ?: "sp_2"
            list.add(ScriptSegment(speakerId = sp1, text = "Bonjour à tous et bienvenue dans ce nouvel épisode de notre podcast !"))
            list.add(ScriptSegment(speakerId = sp2, text = "Salut à tous ! C'est un réel plaisir de vous retrouver aujourd'hui."))
            list.add(ScriptSegment(speakerId = sp1, text = "Aujourd'hui, nous explorons la puissance des voix neurales Edge TTS et la création de dialogues."))
            list.add(ScriptSegment(speakerId = sp2, text = "Exactement, avec des voix fluides et un rendu audio de qualité studio !"))
        }

        _segments.value = list
        syncRawTextFromSegments()
    }

    private fun saveSegmentsInternal(list: List<ScriptSegment>) {
        try {
            val array = JSONArray()
            for (seg in list) {
                val obj = JSONObject()
                obj.put("id", seg.id)
                obj.put("speakerId", seg.speakerId)
                obj.put("text", seg.text)
                array.put(obj)
            }
            prefs.edit().putString("saved_segments", array.toString()).apply()
        } catch (ignored: Exception) {}
    }

    fun addSegment(speakerId: String, text: String = "") {
        val newSeg = ScriptSegment(speakerId = speakerId, text = text)
        val updated = _segments.value + newSeg
        _segments.value = updated
        saveSegmentsInternal(updated)
        syncRawTextFromSegments()
    }

    fun updateSegmentText(segmentId: String, newText: String) {
        val updated = _segments.value.map {
            if (it.id == segmentId) it.copy(text = newText) else it
        }
        _segments.value = updated
        saveSegmentsInternal(updated)
        syncRawTextFromSegments()
    }

    fun updateSegmentSpeaker(segmentId: String, newSpeakerId: String) {
        val updated = _segments.value.map {
            if (it.id == segmentId) it.copy(speakerId = newSpeakerId) else it
        }
        _segments.value = updated
        saveSegmentsInternal(updated)
        syncRawTextFromSegments()
    }

    fun moveSegment(fromIndex: Int, toIndex: Int) {
        val list = _segments.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _segments.value = list
            saveSegmentsInternal(list)
            syncRawTextFromSegments()
        }
    }

    fun deleteSegment(segmentId: String) {
        val updated = _segments.value.filterNot { it.id == segmentId }
        _segments.value = updated
        saveSegmentsInternal(updated)
        syncRawTextFromSegments()
    }

    fun setRawScriptText(text: String) {
        _rawScriptText.value = text
    }

    fun parseRawScriptText() {
        val text = _rawScriptText.value
        if (text.isBlank()) return

        val currentSpeakers = _speakers.value
        val regex = Regex("""\[([^\]]+)\]\s*([^\[]+)""")
        val matches = regex.findAll(text).toList()

        val parsedSegments = mutableListOf<ScriptSegment>()

        if (matches.isNotEmpty()) {
            for (match in matches) {
                val speakerLabel = match.groupValues[1].trim()
                val segmentText = match.groupValues[2].trim()

                // Find matching speaker by name (case-insensitive)
                var matchedSpeaker = currentSpeakers.firstOrNull {
                    it.name.equals(speakerLabel, ignoreCase = true)
                }

                // If not found and space remains, auto-create speaker
                if (matchedSpeaker == null && _speakers.value.size < 5) {
                    addSpeaker(speakerLabel, "fr-FR-DeniseNeural")
                    matchedSpeaker = _speakers.value.lastOrNull()
                }

                val finalSpeakerId = matchedSpeaker?.id ?: currentSpeakers.firstOrNull()?.id ?: "sp_1"
                if (segmentText.isNotEmpty()) {
                    parsedSegments.add(ScriptSegment(speakerId = finalSpeakerId, text = segmentText))
                }
            }
        } else {
            // Split by lines if no brackets
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var toggle = 0
            for (line in lines) {
                val sp = currentSpeakers.getOrNull(toggle % currentSpeakers.size) ?: currentSpeakers.first()
                parsedSegments.add(ScriptSegment(speakerId = sp.id, text = line))
                toggle++
            }
        }

        if (parsedSegments.isNotEmpty()) {
            _segments.value = parsedSegments
            saveSegmentsInternal(parsedSegments)
        }
    }

    private fun syncRawTextFromSegments() {
        val speakerMap = _speakers.value.associateBy { it.id }
        val builder = StringBuilder()
        for (seg in _segments.value) {
            val name = speakerMap[seg.speakerId]?.name ?: "Intervenant"
            builder.append("[$name] ").append(seg.text).append("\n\n")
        }
        _rawScriptText.value = builder.toString().trim()
    }

    fun loadSampleDialog(sampleIndex: Int = 0) {
        val sp1 = _speakers.value.getOrNull(0)?.id ?: "sp_1"
        val sp2 = _speakers.value.getOrNull(1)?.id ?: "sp_2"

        val sample = when (sampleIndex) {
            1 -> listOf(
                ScriptSegment(speakerId = sp1, text = "Bonjour Henri ! Alors, qu'as-tu pensé des dernières avancées sur l'intelligence artificielle ?"),
                ScriptSegment(speakerId = sp2, text = "C'est fascinant Denise ! Surtout la clarté et l'expressivité avec lesquelles nous dialoguons ici."),
                ScriptSegment(speakerId = sp1, text = "Exactement, le pont Rust garantit une réactivité optimale et aucun intermédiaire inutile."),
                ScriptSegment(speakerId = sp2, text = "Et le fichier audio final peut être directement partagé ou écouté en un clin d'œil !")
            )
            else -> listOf(
                ScriptSegment(speakerId = sp1, text = "Bienvenue dans ce studio podcast interactif."),
                ScriptSegment(speakerId = sp2, text = "Merci Denise ! C'est un réel plaisir de co-animer cette session avec toi."),
                ScriptSegment(speakerId = sp1, text = "Chaque réplique est synthétisée avec une voix dédiée et fusionnée avec un silence naturel de 350 millisecondes."),
                ScriptSegment(speakerId = sp2, text = "Le résultat est parfait pour créer des tutoriels, des fictions audio ou des résumés d'actualités.")
            )
        }
        _segments.value = sample
        saveSegmentsInternal(sample)
        syncRawTextFromSegments()
    }

    /**
     * Synthesizes all segments using Rust JNI `synthesizeMp3`, inserts 350ms of silence,
     * and exports the final MP3 into /Music/EdgeTTSPodcasts/.
     */
    fun generatePodcast() {
        val currentSegments = _segments.value.filter { it.text.trim().isNotEmpty() }
        if (currentSegments.isEmpty()) {
            _generationState.value = GenerationState.Error("Le dialogue est vide. Veuillez ajouter du texte.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _generationState.value = GenerationState.Progress(
                currentStep = 0,
                totalSteps = currentSegments.size,
                speakerName = "",
                message = "Démarrage de la synthèse vocale..."
            )

            val speakerMap = _speakers.value.associateBy { it.id }
            val mp3Buffers = mutableListOf<ByteArray>()

            try {
                for ((index, segment) in currentSegments.withIndex()) {
                    val speaker = speakerMap[segment.speakerId] ?: _speakers.value.first()
                    _generationState.value = GenerationState.Progress(
                        currentStep = index + 1,
                        totalSteps = currentSegments.size,
                        speakerName = speaker.name,
                        message = "Synthèse segment ${index + 1}/${currentSegments.size} (${speaker.name})..."
                    )

                    // Direct call to Rust JNI synthesizeMp3 with conversational prosody
                    val mp3Chunk = EdgeTtsNative.synthesizeMp3(
                        segment.text,
                        speaker.voiceShortName,
                        _currentStyle.value.rateMod,
                        "+0%",
                        _currentStyle.value.pitchMod
                    )

                    if (mp3Chunk == null || mp3Chunk.isEmpty()) {
                        throw Exception("Erreur lors de la synthèse pour ${speaker.name}")
                    }
                    mp3Buffers.add(mp3Chunk)
                }

                _generationState.value = GenerationState.Progress(
                    currentStep = currentSegments.size,
                    totalSteps = currentSegments.size,
                    speakerName = "",
                    message = "Fusion finale et insertion des silences naturels (${_currentStyle.value.pauseBetweenTurnsMs}ms)..."
                )

                // Combine MP3 buffers with dynamic natural silence between speakers
                val finalOutputStream = ByteArrayOutputStream()
                val silenceBytes = EdgeTtsDirectClient.getSilenceMp3(_currentStyle.value.pauseBetweenTurnsMs)

                for (i in mp3Buffers.indices) {
                    finalOutputStream.write(mp3Buffers[i])
                    // Insert natural silence between segments
                    if (i < mp3Buffers.size - 1) {
                        finalOutputStream.write(silenceBytes)
                    }
                }

                val finalMp3Bytes = finalOutputStream.toByteArray()

                // Save to /Music/EdgeTTSPodcasts/
                val outputFile = saveFinalMp3File(finalMp3Bytes)
                val durationMs = extractDuration(outputFile)

                val podcast = GeneratedPodcast(
                    file = outputFile,
                    name = outputFile.name,
                    durationMs = durationMs,
                    sizeBytes = outputFile.length(),
                    formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                )

                loadHistory()
                _generationState.value = GenerationState.Success(podcast)
                playAudioFile(outputFile)

            } catch (e: Exception) {
                Log.e("PodcastViewModel", "Generation error", e)
                _generationState.value = GenerationState.Error(
                    e.message ?: "Une erreur est survenue pendant la génération du podcast."
                )
            }
        }
    }

    private fun saveFinalMp3File(mp3Bytes: ByteArray): File {
        val app = getApplication<Application>()
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        var targetDir = File(musicDir, "EdgeTTSPodcasts")

        if (!targetDir.exists()) {
            val created = targetDir.mkdirs()
            if (!created && !targetDir.exists()) {
                // Fallback to app external files dir if scoped storage forbids direct creation
                targetDir = File(app.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "EdgeTTSPodcasts")
                targetDir.mkdirs()
            }
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val finalFile = File(targetDir, "podcast_$timestamp.mp3")

        FileOutputStream(finalFile).use { fos ->
            fos.write(mp3Bytes)
            fos.flush()
        }

        // Notify MediaScanner so it appears immediately in music apps
        try {
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(finalFile)
            app.sendBroadcast(intent)
        } catch (ignored: Exception) {}

        return finalFile
    }

    private fun extractDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val dirs = listOf(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "EdgeTTSPodcasts"),
                File(app.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "EdgeTTSPodcasts")
            )

            val podcasts = mutableListOf<GeneratedPodcast>()
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

            for (dir in dirs) {
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".mp3") } ?: emptyArray()
                    for (f in files) {
                        podcasts.add(
                            GeneratedPodcast(
                                file = f,
                                name = f.name,
                                durationMs = extractDuration(f),
                                sizeBytes = f.length(),
                                formattedDate = sdf.format(Date(f.lastModified()))
                            )
                        )
                    }
                }
            }

            podcasts.sortByDescending { it.file.lastModified() }
            _historyPodcasts.value = podcasts
        }
    }

    fun playAudioFile(file: File) {
        if (!file.exists()) return

        stopPlayback()

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            mediaPlayer = player

            _playerState.value = PlayerUiState(
                isPlaying = true,
                currentPositionMs = 0L,
                totalDurationMs = player.duration.toLong(),
                activeFile = file,
                activeTitle = file.name
            )

            player.setOnCompletionListener {
                _playerState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                stopProgressTracking()
            }

            startProgressTracking()
        } catch (e: Exception) {
            Log.e("PodcastViewModel", "Playback error", e)
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _playerState.update { it.copy(isPlaying = false) }
            stopProgressTracking()
        } else {
            player.start()
            _playerState.update { it.copy(isPlaying = true) }
            startProgressTracking()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        player.seekTo(positionMs.toInt())
        _playerState.update { it.copy(currentPositionMs = positionMs) }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        playbackProgressJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    _playerState.update {
                        it.copy(
                            currentPositionMs = player.currentPosition.toLong(),
                            totalDurationMs = player.duration.toLong()
                        )
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracking() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
    }

    private fun stopPlayback() {
        stopProgressTracking()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
    }

    fun sharePodcast(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Partager le podcast"))
        } catch (e: Exception) {
            Log.e("PodcastViewModel", "Share error: ${e.message}")
        }
    }

    fun openPodcast(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/mpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("PodcastViewModel", "Open error: ${e.message}")
        }
    }

    fun deleteHistoryFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists()) {
                file.delete()
            }
            loadHistory()
            if (_playerState.value.activeFile?.absolutePath == file.absolutePath) {
                withContext(Dispatchers.Main) {
                    stopPlayback()
                    _playerState.value = PlayerUiState()
                }
            }
        }
    }

    fun resetGenerationState() {
        _generationState.value = GenerationState.Idle
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
