package com.audio_arcade.arcadeshare.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.audio_arcade.arcadeshare.ArcadeShare
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal class ArcadeWebSocket(
    private val serverUrl:   String,
    private val onOpen:      () -> Unit,
    private val onMessage:   (String) -> Unit,
    private val onError:     (String) -> Unit,
    private val onClosed:    () -> Unit,
    private val maxAttempts: Int  = 5,
    private val maxDelayMs:  Long = 10_000L
) {
    private companion object {
        const val PONG = """{"type":"pong"}"""
        /** Début exact du message envoyé par le serveur quand il refuse l'application. */
        const val AUTH_REJECTED = "{\"type\":\"auth_rejected\""
        const val MAX_PENDING = 20
    }

    private var ws:               WebSocket? = null
    private val handler           = Handler(Looper.getMainLooper())
    private val pending           = ArrayDeque<String>()   // protégée par synchronized(pending)
    @Volatile private var isOpen        = false
    @Volatile private var authenticated = false
    private var shouldReconnect   = true
    private var reconnectAttempts = 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0,  TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val pingRunnable = object : Runnable {
        override fun run() {
            if (isOpen && authenticated) {
                ws?.send("""{"type":"ping"}""")
                handler.postDelayed(this, 25_000)
            }
        }
    }

    fun connect() {
        shouldReconnect = true
        authenticated = false
        val req = Request.Builder().url(serverUrl).build()
        ws = client.newWebSocket(req, listener)
    }

    fun send(msg: String) {
        if (isOpen && authenticated) {
            ws?.send(msg)
            return
        }
        synchronized(pending) {
            if (pending.size >= MAX_PENDING) pending.removeFirst()
            pending.addLast(msg)
        }
    }

    fun disconnect() {
        shouldReconnect = false
        handler.removeCallbacks(pingRunnable)
        ws?.close(1000, "Déconnexion normale")
        ws = null
        isOpen = false
        authenticated = false
        synchronized(pending) { pending.clear() }
    }

    /** Reconnexion avec délai croissant ; au bout de [maxAttempts] on prévient l'appelant. */
    private fun retryOrGiveUp(giveUp: () -> Unit) {
        handler.removeCallbacks(pingRunnable)
        if (shouldReconnect && reconnectAttempts < maxAttempts) {
            reconnectAttempts++
            val delay = (reconnectAttempts * 2000L).coerceAtMost(maxDelayMs)
            handler.postDelayed({ if (shouldReconnect) connect() }, delay)
        } else {
            giveUp()
        }
    }

    private val listener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            isOpen = true
            authenticated = false
            // Handshake : doit être le tout premier message
            webSocket.send(JSONObject().apply {
                put("type", "auth")
                put("appId", ArcadeShare.appId)
                put("versionId", ArcadeShare.versionId)
            }.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == PONG) return

            // Refus de l'appli, y compris une révocation à chaud APRÈS l'authentification :
            // inutile de se reconnecter, le serveur dira non à chaque fois
            if (text.startsWith(AUTH_REJECTED)) {
                val reason = runCatching { JSONObject(text).optString("reason") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "Application non autorisée"
                handler.post {
                    shouldReconnect = false
                    onError(reason)
                }
                return
            }

            if (!authenticated) {
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (json?.optString("type") == "auth_ok") {
                    if (json.optBoolean("deprecated")) {
                        Log.w("ArcadeShare", "SDK déprécié : ${json.optString("reason")}")
                    }
                    ArcadeShare.isCertified = json.optBoolean("certified", false)
                    handler.post {
                        if (!shouldReconnect) return@post
                        authenticated = true
                        reconnectAttempts = 0          // connexion réellement établie
                        handler.removeCallbacks(pingRunnable)
                        onOpen()   // envoie create_room / rejoin_room / join_room
                        val queued = synchronized(pending) {
                            pending.toList().also { pending.clear() }
                        }
                        queued.forEach { webSocket.send(it) }
                        handler.postDelayed(pingRunnable, 25_000)
                    }
                    return
                }
            }
            handler.post { onMessage(text) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isOpen = false
            authenticated = false
            // Fermeture par le serveur (redéploiement...) : on se reconnecte aussi
            handler.post { retryOrGiveUp { onClosed() } }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isOpen = false
            authenticated = false
            handler.post { retryOrGiveUp { onError(t.message ?: "Erreur de connexion") } }
        }
    }
}