package com.audio_arcade.arcadeshare

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import com.audio_arcade.arcadeshare.callback.HostCallback
import com.audio_arcade.arcadeshare.model.RoomCode
import com.audio_arcade.arcadeshare.model.ShareState
import com.audio_arcade.arcadeshare.network.ArcadeWebSocket
import com.audio_arcade.arcadeshare.network.DeviceId
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ArcadeShareHost(
    private val context:   Context,
    private val serverUrl: String,   // wss://...
    private val httpUrl:   String,   // https://...
    private val username:  String
) {
    private var callback:      HostCallback?    = null
    private var ws:            ArcadeWebSocket? = null
    private var currentRoom:   RoomCode?        = null
    private var requestedCode: String?          = null   // code demandé via rejoinRoom()
    private val handler        = Handler(Looper.getMainLooper())
    private val deviceSerial   by lazy { DeviceId.get(context) }

    /** Appelé par ArcadeShare quand la room est prête (créée ou rejointe). */
    internal var onRoomReady: ((ArcadeShareHost) -> Unit)? = null

    /** Room courante (null tant qu'elle n'est pas confirmée par le serveur). */
    val room: RoomCode? get() = currentRoom

    /** Code de la room de ce host (confirmé, ou demandé si on est en train de rejoindre). */
    val roomCode: String? get() = currentRoom?.code ?: requestedCode

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── État de lecture ───────────────────────────────────────────────────────
    private var lastState:   ShareState? = null
    private var lastStateAt: Long        = 0L
    private var socketGen = 0

    val isActive: Boolean get() = ws != null

    internal var onActivityChanged: (() -> Unit)? = null

    private val broadcastRunnable = object : Runnable {
        override fun run() {
            val room = currentRoom ?: return
            lastState?.let { sendStateToServer(estimate(it), room.code) }
            handler.postDelayed(this, 2000)
        }
    }

    /** Avance la position depuis le dernier updateState() pour que le re-broadcast reste juste. */
    private fun estimate(state: ShareState): ShareState {
        if (!state.isPlaying) return state
        val pos = state.position + (SystemClock.elapsedRealtime() - lastStateAt)
        return state.copy(
            position = if (state.duration > 0) pos.coerceAtMost(state.duration) else pos
        )
    }

    // ── Upload de la piste ────────────────────────────────────────────────────
    private var uploadedKey:  String?            = null
    private var uploadedName: String?            = null
    private var uploadedSize: Long               = -1L
    private var uploadingKey: String?            = null
    private var wantedKey:    String?            = null
    private var failedKey:    String?            = null
    private var pendingTrack: Pair<String, Uri>? = null
    private var uploadRetries = 0
    private var pendingJoin   = false

    /** Appelé quand le serveur refuse la création/réactivation : ArcadeShare retire ce host. */
    internal var onFailed: ((ArcadeShareHost) -> Unit)? = null

    fun setCallback(cb: HostCallback?) { callback = cb }

    // ── Connexion ─────────────────────────────────────────────────────────────
    fun createRoom() {
        requestedCode = null
        openSocket()
    }

    fun rejoinRoom(code: String) {
        requestedCode = code.trim().uppercase()
        openSocket()
    }

    /**
     * Sur chaque (re)connexion : si on connaît déjà le code, on rejoint la room
     * au lieu d'en créer une nouvelle (sinon une reconnexion réseau changerait le code).
     */
    private fun openSocket() {
        ws?.disconnect()
        val gen = ++socketGen
        ws = ArcadeWebSocket(
            serverUrl   = serverUrl,
            onOpen      = {
                if (gen == socketGen) {
                    pendingJoin = true
                    val code = currentRoom?.code ?: requestedCode
                    ws?.send(
                        if (code == null) authMessage("create_room")
                        else authMessage("rejoin_room", code)
                    )
                }
            },
            onMessage   = { raw -> if (gen == socketGen) handleMessage(raw) },
            onError     = { err -> if (gen == socketGen) { markInactive(); callback?.onError(err) } },
            onClosed    = { if (gen == socketGen) { markInactive(); callback?.onDisconnected() } },
            // ≈ 5 min de tentatives : la durée pendant laquelle le serveur garde la piste (HOST_GRACE_MS)
            maxAttempts = 25,
            maxDelayMs  = 15_000L
        )
        ws?.connect()
    }

    private fun authMessage(type: String, code: String? = null): String =
        JSONObject().apply {
            put("type", type)
            if (code != null) put("code", code)
            put("username", username)
            put("deviceSerial", deviceSerial)
        }.toString()

    // ── Diffusion de l'état ───────────────────────────────────────────────────
    /** Met à jour l'état de lecture et le diffuse aux invités (sans ré-upload). */
    fun updateState(state: ShareState) {
        lastState   = state
        lastStateAt = SystemClock.elapsedRealtime()
        currentRoom?.code?.let { sendStateToServer(state, it) }
    }

    /**
     * Upload la piste [key] (ex: mediaId) si elle n'a pas déjà été envoyée à cette room.
     * Les uploads sont sérialisés : si une piste change pendant un upload, la dernière gagne.
     */
    fun shareTrack(key: String, uri: Uri) {
        wantedKey = key
        if (currentRoom == null) return
        if (key == uploadedKey || key == failedKey || key == uploadingKey) return
        if (uploadingKey != null) {
            pendingTrack = key to uri
            return
        }
        uploadingKey = key
        upload(uri) { code, name, size ->
            uploadingKey = null
            when {
                code in 200..299 -> {
                    uploadedKey = key; uploadedName = name; uploadedSize = size
                    uploadRetries = 0
                }
                code == 413 || code == 415 -> {
                    failedKey = key
                    callback?.onError(
                        if (code == 413) "Fichier trop volumineux (200 Mo max)"
                        else "Format audio non pris en charge"
                    )
                }
                // Réseau, 5xx, 403 « hôte non connecté » : 3 essais espacés, seulement si la piste est toujours la bonne
                uploadRetries < 3 && pendingTrack == null && currentRoom != null -> {
                    uploadRetries++
                    handler.postDelayed({ if (wantedKey == key) shareTrack(key, uri) }, 3_000L * uploadRetries)
                }
                else -> uploadRetries = 0
            }
            pendingTrack?.let { (k, u) ->
                pendingTrack = null
                shareTrack(k, u)
            }
        }
    }

    fun uploadMusic(uri: Uri, onResult: (Boolean) -> Unit) =
        upload(uri) { code, _, _ -> onResult(code in 200..299) }

    private fun upload(uri: Uri, onResult: (code: Int, name: String, size: Long) -> Unit) {
        val room = currentRoom ?: return onResult(-1, "", -1L)

        val (rawName, size) = queryMeta(uri)
        val mime = context.contentResolver.getType(uri) ?: getMimeTypeForFile(rawName)
        val name = ensureAudioExtension(rawName, mime)

        val fileBody = object : RequestBody() {
            override fun contentType(): MediaType? = mime.toMediaTypeOrNull()
            override fun contentLength(): Long = size
            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { sink.writeAll(it.source()) }
            }
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("music", name, fileBody)
            .build()

        val request = Request.Builder()
            .url("$httpUrl/upload-music")
            .addHeader("x-room-code", room.code)
            .addHeader("x-device-serial", deviceSerial)
            .addHeader("x-app-id", ArcadeShare.appId)
            .addHeader("x-version-id", ArcadeShare.versionId)
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                android.util.Log.e("ArcadeShare", "Upload failure: ${e.message}")
                handler.post { onResult(-1, name, size) }
            }
            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                android.util.Log.d("ArcadeShare", "Upload response: code=$code")
                response.close()
                handler.post { onResult(code, name, size) }
            }
        })
    }

    private fun verifyUploadedTrack(then: () -> Unit) {
        val key  = uploadedKey
        val name = uploadedName
        val size = uploadedSize
        val code = currentRoom?.code
        if (key == null || name == null || size <= 0 || code == null) {
            uploadedKey = null
            then()
            return
        }

        val request = Request.Builder()
            .url("$httpUrl/music/$code?${ArcadeShare.authQuery()}")
            .head()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            private fun finish(same: Boolean) {
                handler.post {
                    if (!same && uploadedKey == key) uploadedKey = null
                    then()
                }
            }
            override fun onFailure(call: Call, e: java.io.IOException) { finish(false) }
            override fun onResponse(call: Call, response: Response) {
                val sameSize = response.code == 200 &&
                        response.header("Content-Length")?.toLongOrNull() == size
                val sameName = response.header("Content-Disposition")
                    ?.substringAfter("filename*=UTF-8''", "")
                    ?.let { Uri.decode(it) } == name
                response.close()
                finish(sameSize && sameName)
            }
        })
    }

    fun resume() {
        if (ws != null) return
        roomCode?.let { rejoinRoom(it) }
    }

    private fun markInactive() {
        socketGen++
        handler.removeCallbacks(broadcastRunnable)
        requestedCode = roomCode
        currentRoom = null
        ws?.disconnect()
        ws = null
        onActivityChanged?.invoke()
    }

    private fun queryMeta(uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = -1L
        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0) name = c.getString(n)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        } else {
            uri.path?.let { size = File(it).length() }
        }
        return (name ?: uri.lastPathSegment ?: "track") to size
    }

    private fun getMimeTypeForFile(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3"  -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav"  -> "audio/wav"
            "ogg"  -> "audio/ogg"
            "aac"  -> "audio/aac"
            "m4a"  -> "audio/mp4"
            "opus" -> "audio/opus"
            else   -> "application/octet-stream"
        }
    }

    /** Ajoute l'extension audio qui correspond au type MIME quand le nom du fichier n'en a pas. */
    private fun ensureAudioExtension(name: String, mime: String): String {
        val known = setOf("mp3", "flac", "wav", "ogg", "aac", "m4a", "opus")
        if (name.substringAfterLast('.', "").lowercase() in known) return name

        val ext = when (mime.lowercase()) {
            "audio/mpeg", "audio/mp3"                 -> "mp3"
            "audio/flac", "audio/x-flac"              -> "flac"
            "audio/wav", "audio/x-wav", "audio/wave"  -> "wav"
            "audio/ogg", "application/ogg"            -> "ogg"
            "audio/aac", "audio/aacp"                 -> "aac"
            "audio/mp4", "audio/x-m4a", "audio/m4a"   -> "m4a"
            "audio/opus"                              -> "opus"
            else                                      -> null
        }
        return if (ext != null) "$name.$ext" else name
    }

    // ── Fin de session ────────────────────────────────────────────────────────
    /** Suppression réelle de la room côté serveur. */
    fun deleteRoom() {
        android.util.Log.w("ArcadeShare", "deleteRoom() appelé (room=$roomCode)", Throwable("appelant"))
        handler.removeCallbacks(broadcastRunnable)
        if (roomCode != null) ws?.send("""{"type":"delete_room"}""")
        currentRoom   = null
        requestedCode = null
        ws?.disconnect()
        ws = null
    }

    /** Quitte la room SANS la supprimer (elle pourra être rejointe via rejoinRoom). */
    fun stopBroadcasting() {
        ws?.send("""{"type":"leave_room"}""")
        markInactive()
    }

    fun release() {
        stopBroadcasting()
        callback          = null
        onRoomReady       = null
        onActivityChanged = null
    }

    private fun sendStateToServer(state: ShareState, code: String) {
        val json = JSONObject().apply {
            put("type",       "state")
            put("code",       code)
            put("position",   state.position / 1000.0)   // le serveur attend des secondes
            put("isPlaying",  state.isPlaying)
            put("title",      state.title)
            put("artist",     state.artist)
            put("artworkUrl", state.artworkUrl)
            put("duration",   state.duration / 1000.0)
            put("nextTitle",  state.nextTitle)
        }
        ws?.send(json.toString())
    }

    private fun handleMessage(raw: String) {
        val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return

        when (msg.optString("type")) {
            "room_created", "room_rejoined" -> {
                val code = msg.optString("code", requestedCode ?: "")
                if (code.isEmpty()) return
                pendingJoin = false
                currentRoom = RoomCode(code = code, guestCount = msg.optInt("guestCount", 0))
                callback?.onRoomCreated(currentRoom!!)
                handler.removeCallbacks(broadcastRunnable)
                handler.postDelayed(broadcastRunnable, 2000)
                // (l'ancien « uploadedKey = null » disparaît : on vérifie d'abord ce que le serveur a gardé)
                verifyUploadedTrack { onRoomReady?.invoke(this) }
            }
            "guest_joined" -> {
                val count = msg.optInt("guestCount", 0)
                val uname = msg.optString("username", "Inconnu")
                currentRoom = currentRoom?.copy(guestCount = count)
                currentRoom?.let { callback?.onGuestJoined(it, uname) }
            }
            "guest_left" -> {
                val count = msg.optInt("guestCount", 0)
                val uname = msg.optString("username", "Inconnu")
                currentRoom = currentRoom?.copy(guestCount = count)
                currentRoom?.let { callback?.onGuestLeft(it, uname) }
            }
            "error" -> {
                val message = msg.optString("message", "Erreur inconnue")
                if (pendingJoin) {
                    // Création / réactivation refusée : on libère la connexion au lieu de la laisser ouverte
                    markInactive()
                    onFailed?.invoke(this)
                }
                callback?.onError(message)
            }
        }
    }
}