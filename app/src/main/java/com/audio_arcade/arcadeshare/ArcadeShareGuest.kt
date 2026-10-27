package com.audio_arcade.arcadeshare

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.audio_arcade.arcadeshare.callback.GuestCallback
import com.audio_arcade.arcadeshare.model.ShareState
import com.audio_arcade.arcadeshare.network.ArcadeWebSocket
import com.audio_arcade.arcadeshare.network.DeviceId
import org.json.JSONObject

class ArcadeShareGuest(
    private val context:   Context,
    private val serverUrl: String,
    private val httpUrl:   String,
    private val username:  String
) {
    private var callback:  GuestCallback?   = null
    private var ws:        ArcadeWebSocket? = null
    private var roomCode:  String           = ""
    private val handler    = Handler(Looper.getMainLooper())

    internal var onReleased: (() -> Unit)? = null

    fun setCallback(cb: GuestCallback) { callback = cb }

    fun joinRoom(code: String) {
        ws?.disconnect()   // un seul socket à la fois
        roomCode = code.trim().uppercase()

        // JSONObject : un pseudo contenant un guillemet ou un antislash ne casse plus le message
        val join = JSONObject().apply {
            put("type",         "join_room")
            put("code",         roomCode)
            put("username",     username)
            put("deviceSerial", getSerial())
        }.toString()

        ws = ArcadeWebSocket(
            serverUrl = serverUrl,
            onOpen    = { ws?.send(join) },
            onMessage = { raw -> handleMessage(raw) },
            onError   = { err -> callback?.onError(err) },
            onClosed  = { callback?.onDisconnected() }
        )
        ws?.connect()
    }

    fun leaveRoom() {
        if (roomCode.isNotEmpty()) {
            ws?.send(JSONObject().apply {
                put("type", "leave_room")
                put("code", roomCode)
            }.toString())
        }
        ws?.disconnect()
        ws = null
        roomCode = ""
    }

    fun release() {
        leaveRoom()
        callback = null
        onReleased?.invoke()
        onReleased = null
    }

    fun getStreamUrl(): String? {
        if (roomCode.isEmpty()) return null
        return "$httpUrl/music/$roomCode?${ArcadeShare.authQuery()}"
    }

    private fun buildState(json: JSONObject): ShareState {
        val base = json.toShareState()
        if (base.currentFile.isEmpty()) return base
        // appId/versionId : exigés si MUSIC_REQUIRE_AUTH=true ; « f » évite qu'ExoPlayer resserve l'ancien fichier
        val url = "$httpUrl/music/$roomCode?${ArcadeShare.authQuery()}&f=${Uri.encode(base.currentFile)}"
        return base.copy(audioUrl = url)
    }

    private fun handleMessage(raw: String) {
        val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return

        when (msg.optString("type")) {
            "room_joined" -> {
                val hostName   = msg.optString("hostName", "Hôte")
                val guestCount = msg.optInt("guestCount", 0)
                val lastState  = msg.optJSONObject("lastState")?.let { buildState(it) }
                val hostCertified = if (msg.isNull("hostCertified")) null else msg.optBoolean("hostCertified")
                callback?.onHostCertification(hostCertified)
                callback?.onRoomJoined(roomCode, hostName, guestCount, lastState)
                lastState?.let { callback?.onStateReceived(it) }
            }

            "state" -> {
                val state = buildState(msg)
                callback?.onStateReceived(state)
            }

            "host_left" -> callback?.onHostLeft()

            "error" -> callback?.onError(msg.optString("message", "Erreur inconnue"))
        }
    }

    private fun getSerial(): String = DeviceId.get(context)
}

/** Au-delà de ce délai, l'écart d'horloge est considéré comme faux et ignoré. */
private const val MAX_LATENCY_MS = 5_000L

// ── Extension JSONObject → ShareState ─────────────────────────────────────────
internal fun JSONObject.toShareState(): ShareState {
    val serverTs = optLong("timestamp", 0L)

    // Latence estimée = heure locale - heure du serveur. Si l'horloge du téléphone est en avance ou
    // en retard (ou si l'état date d'avant un redémarrage du serveur), l'écart n'a aucun sens :
    // on l'ignore plutôt que de décaler la lecture de plusieurs minutes.
    val rawLatency = if (serverTs > 0) System.currentTimeMillis() - serverTs else 0L
    val latency    = if (rawLatency in 0L..MAX_LATENCY_MS) rawLatency else 0L

    // Le serveur envoie position/duration en SECONDES (voir ArcadeShareHost.sendStateToServer) → conversion en ms
    val positionMs = (optDouble("position", 0.0) * 1000).toLong()
    val durationMs = (optDouble("duration", 0.0) * 1000).toLong()

    return ShareState(
        title       = optString("title",       ""),
        artist      = optString("artist",      ""),
        artworkUrl  = optString("artworkUrl",  ""),
        audioUrl    = "",   // reconstruit dans buildState()
        currentFile = optString("currentFile", ""),
        position    = positionMs + latency,
        duration    = durationMs,
        isPlaying   = optBoolean("isPlaying", false),
        nextTitle   = optString("nextTitle",  ""),
        timestamp   = System.currentTimeMillis()
    )
}