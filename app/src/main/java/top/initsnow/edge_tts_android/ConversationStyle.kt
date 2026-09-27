package top.initsnow.edge_tts_android

/**
 * Conversation styles for podcast dialogues with specialized prosody and natural pauses.
 */
enum class ConversationStyle(
    val title: String,
    val description: String,
    val pauseBetweenTurnsMs: Int,
    val rateMod: String,
    val pitchMod: String
) {
    CHALEUREUX(
        title = "Chaleureux",
        description = "Amical, bienveillant, complice et empathique",
        pauseBetweenTurnsMs = 380,
        rateMod = "+0%",
        pitchMod = "+0Hz"
    ),
    DEBAT(
        title = "Débat",
        description = "Rythmé, contradictoire, incisif et vif",
        pauseBetweenTurnsMs = 240,
        rateMod = "+4%",
        pitchMod = "+2Hz"
    ),
    EMISSION(
        title = "Émission Pro",
        description = "Journalistique, posé, clair et structuré",
        pauseBetweenTurnsMs = 450,
        rateMod = "-2%",
        pitchMod = "-1Hz"
    ),
    DETENDU(
        title = "Détendu / Humour",
        description = "Spontané, taquin, anecdotique avec rires légers",
        pauseBetweenTurnsMs = 320,
        rateMod = "+2%",
        pitchMod = "+1Hz"
    )
}
