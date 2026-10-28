# Audio_Arcade_SDK — Android

## Arcade Share Server · Kotlin

> SDK de synchronisation audio en temps réel entre appareils. L'URL du serveur est intégrée au SDK : ton application n'a plus qu'à fournir son `appId`.

[![](https://jitpack.io/v/Audio-Arcade/Audio_Arcade_SDK_Android.svg)](https://jitpack.io/#Audio-Arcade/Audio_Arcade_SDK_Android)

📱 **Android (Kotlin)** &nbsp;·&nbsp; 🪟 [Windows](https://github.com/Audio-Arcade/Audio_Arcade_SDK)

---

## 📑 Sommaire

1. [Authentification (appId)](#-authentification-appid)
2. [Installation](#1-installation)
3. [ID de l'appareil](#2-id-de-lappareil)
4. [Mode Hôte — Cast](#3-mode-hôte--cast)
5. [Rooms permanentes](#4-rooms-permanentes)
6. [Mode Invité — Live](#5-mode-invité--live)
7. [Envoi de musique](#6-envoi-de-musique)
8. [Référence des méthodes](#7-référence-des-méthodes)
9. [Référence des événements](#8-référence-des-événements)

---

## 🔑 Authentification (appId)

Chaque application qui utilise ArcadeShare doit posséder son propre `appId`, fourni par l'équipe Audio_Arcade ([formulaire de contact](https://audio-arcade.odoo.com/contactus)). Il permet d'autoriser ou de révoquer l'accès application par application.

| Élément | Qui le fournit | Rôle |
|---|---|---|
| `appId` | Toi (obtenu auprès d'Audio_Arcade) | Identifie ton application auprès du serveur |
| `versionId` | Le SDK (intégré, ne pas modifier) | Identifie la version du SDK utilisée |

| Situation | Comportement sous Android |
|---|---|
| `appId` inconnu ou désactivé | `onError` avec le message du serveur |
| Version du SDK dépréciée (fonctionne encore) | Avertissement dans le logcat (tag `ArcadeShare`) |
| Version du SDK inconnue | Connexion refusée : télécharge la dernière version du SDK |

> ⚠️ Ne modifie pas la valeur de `versionId` dans le code du SDK : le serveur connaît la correspondance réelle entre cet identifiant et la version publiée, et refuse une version dépréciée ou inconnue.

---

## 1. Installation

### Option A — Gradle (recommandé)

Le SDK est distribué via JitPack. Ajoute le dépôt dans `settings.gradle.kts` :

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Puis la dépendance dans le `build.gradle.kts` du module app :

```kotlin
dependencies {
    implementation("com.github.Audio-Arcade:Audio_Arcade_SDK_Android:android-2.0.2")
}
```

### Option B — Fichier AAR

**[⬇ Télécharger arcadeshare-2.0.2.aar](https://github.com/Audio-Arcade/Audio_Arcade_SDK_Android/releases/download/2.0.2/Audio_Arcade_SDK_Android-2.0.2.aar)**

Copie le fichier dans `app/libs/` et déclare-le avec sa dépendance (OkHttp n'est pas embarqué dans un AAR) :

```kotlin
dependencies {
    implementation(files("libs/arcadeshare-2.0.2.aar"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

> ℹ️ La permission `INTERNET` est déclarée par le SDK et fusionnée automatiquement dans le manifeste de ton application.

### Imports et initialisation

```kotlin
import com.audio_arcade.arcadeshare.ArcadeShare
import com.audio_arcade.arcadeshare.ArcadeShareHost
import com.audio_arcade.arcadeshare.ArcadeShareGuest
import com.audio_arcade.arcadeshare.callback.HostCallback
import com.audio_arcade.arcadeshare.callback.GuestCallback
import com.audio_arcade.arcadeshare.model.RoomCode
import com.audio_arcade.arcadeshare.model.ShareState
```

Initialise le SDK une seule fois, avant toute utilisation — le plus sûr est dans ta classe `Application` (déclarée par `android:name` dans le manifeste) :

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ArcadeShare.init(this, appId = "TON_APP_ID")   // fourni par Audio_Arcade
    }
}
```

> ℹ️ L'URL du serveur est intégrée au SDK, comme sur Windows : tu n'as qu'à fournir ton `appId`. Pour un serveur de test, une surcharge existe : `ArcadeShare.init(context, appId, serverUrl, httpUrl)`.

---

## 2. ID de l'appareil

```kotlin
val deviceSerial = ArcadeShare.getDeviceSerial()
```

> ℹ️ Identifiant stable de l'appareil. Comme sur Windows, c'est lui qui donne accès aux pseudos réservés et aux rooms permanentes. Il peut changer si l'application est signée avec une autre clé (build debug et release) ou après une réinitialisation d'usine.

---

## 3. Mode Hôte — Cast

Le SDK possède les connexions : une room = un host, géré par l'objet `ArcadeShare`. Ton lecteur n'a qu'à lui signaler la piste et l'état de lecture, le SDK diffuse vers toutes les rooms actives. L'état du partage survit aux changements d'écran, puisqu'il n'est pas lié à une activité.

### Démarrer / arrêter le partage

```kotlin
// codes : les rooms permanentes de l'utilisateur (voir section 4)
ArcadeShare.startSharing(username = "Mon Pseudo", codes = codes)

// Arrêter (les rooms sont conservées, les invités sont déconnectés)
ArcadeShare.stopSharing()
```

### Suivre l'état (ex. couleur d'un bouton)

```kotlin
ArcadeShare.onSharingChanged = { updateButton() }   // appelé sur le thread principal

fun updateButton() {
    when {
        ArcadeShare.isSharing    -> { /* vert : au moins une room est prête */ }
        ArcadeShare.isConnecting -> { /* orange : connexion en cours */ }
        else                     -> { /* rouge : partage coupé */ }
    }
}

// Pense à remettre ArcadeShare.onSharingChanged = null quand l'écran se ferme
```

### Diffuser la piste et l'état depuis ton lecteur

À appeler à chaque événement du lecteur (changement de piste, lecture/pause, déplacement dans la piste) — par exemple dans le `Player.Listener` d'un service Media3 :

```kotlin
// 1. La piste courante : envoyée une seule fois par room (dédupliquée par la clé)
mediaItem.localConfiguration?.uri?.let { uri ->
    ArcadeShare.shareTrack(key = mediaItem.mediaId, uri = uri)
}

// 2. L'état de lecture : envoyé à toutes les rooms actives
ArcadeShare.broadcastState(
    ShareState(
        title     = trackTitle,
        artist    = trackArtist,
        position  = player.currentPosition,   // ms
        duration  = player.duration,          // ms
        isPlaying = player.isPlaying,
        nextTitle = nextTrackTitle
    )
)
```

> ℹ️ Le SDK renvoie lui-même le dernier état et la dernière piste à toute room qui démarre ou se reconnecte, et entretient une position à jour toutes les 2 secondes. Aucune boucle d'envoi n'est nécessaire côté application, et l'upload se fait en flux depuis l'URI (pas de copie du fichier).

### Restaurer les rooms au lancement

```kotlin
// Idempotent : peut être appelé plusieurs fois. Ne relance rien si
// l'utilisateur a coupé le partage avec stopSharing().
ArcadeShare.restoreRooms(username = "Mon Pseudo", codes = codes)
```

> ⚠️ Chaque room accepte au maximum **6 invités**. Au-delà, toute nouvelle tentative de connexion est refusée par le serveur.

---

## 4. Rooms permanentes

Maximum **2 rooms** par appareil, comme sur Windows. Garde les codes dans les préférences de ton application pour pouvoir les réactiver au lancement ; `fetchMyRooms` permet de les retrouver (après une réinstallation, par exemple).

### Créer une room permanente

```kotlin
val host = ArcadeShare.createHost(username = "Mon Pseudo")

host.setCallback(object : HostCallback {
    override fun onRoomCreated(room: RoomCode) {
        // room.code à afficher et à mémoriser (aussi appelé quand une room est réactivée)
    }
    override fun onGuestJoined(room: RoomCode, username: String) { }
    override fun onGuestLeft(room: RoomCode, username: String) { }
    override fun onError(message: String) { }
    override fun onDisconnected() { }
})

host.createRoom()
```

### Lister ses rooms permanentes

```kotlin
ArcadeShare.fetchMyRooms { rooms ->   // appelé sur le thread principal
    // rooms : List<JSONObject> (code, hostName, createdAt, hostOnline, guestCount...)
}
```

### Retrouver le host d'une room

```kotlin
val host: ArcadeShareHost? = ArcadeShare.getHost("ABC123")
val tous: List<ArcadeShareHost> = ArcadeShare.getHosts()

host?.room?.guestCount   // nombre d'invités connectés (null tant que la room n'est pas prête)
```

### Supprimer une room permanente

```kotlin
ArcadeShare.deleteRoom("ABC123") { ok ->   // supprime la room côté serveur, même si le host est hors ligne
    // ok = true : supprimée (ou déjà inexistante)
}
```

---

## 5. Mode Invité — Live

### Rejoindre une room

```kotlin
val guest = ArcadeShare.createGuest(username = "Mon Pseudo")

guest.setCallback(object : GuestCallback {
    override fun onRoomJoined(code: String, hostName: String, guestCount: Int, lastState: ShareState?) {
        // connecté à la room
    }
    override fun onStateReceived(state: ShareState) {
        // mettre à jour l'UI + synchroniser la lecture
    }
    override fun onHostLeft() {
        // l'hôte a quitté la session
    }
    override fun onError(message: String) { }
    override fun onDisconnected() { }
})

guest.joinRoom("ABC123")
```

### Lecture audio

Le SDK synchronise les métadonnées et l'état de lecture, mais ne joue pas le son lui-même : c'est à ton application de consommer le flux et de piloter un lecteur (ex. Media3/ExoPlayer). Une fois qu'un `ShareState` reçu contient un `currentFile` non vide, son champ `audioUrl` pointe vers le flux HTTP de la room :

```kotlin
override fun onStateReceived(state: ShareState) {
    if (state.audioUrl.isEmpty()) return   // pas encore de musique côté hôte

    if (state.currentFile != loadedFile) {
        loadedFile = state.currentFile
        // « ?f= » évite que le lecteur resserve l'ancien fichier pour la même URL
        val uri = "${state.audioUrl}?f=${Uri.encode(state.currentFile)}"
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    // state.position / state.duration sont en millisecondes, déjà corrigés
    // de la latence réseau : resynchronise si l'écart dépasse ~3 secondes.
}
```

> ℹ️ Pour une lecture qui continue en arrière-plan (écran verrouillé, changement d'activité), héberge ton lecteur dans un `MediaSessionService` dédié. Si ton application a déjà une session Media3 (lecteur local), donne un identifiant unique à celle de l'invité avec `MediaSession.Builder(...).setId("guest")`, sinon Media3 refuse la seconde session (« Session ID must be unique »).

### Quitter la room

```kotlin
guest.leaveRoom()   // quitte la room (la room est conservée)
guest.release()     // libère l'instance
```

---

## 6. Envoi de musique

Dans le cas courant, ne l'appelle pas toi-même : `ArcadeShare.shareTrack(key, uri)` (section 3) envoie la piste à toutes les rooms, une seule fois par piste, et renvoie la piste courante à toute room qui démarre. Pour un envoi manuel vers une room précise :

```kotlin
host.uploadMusic(uri) { success ->      // content:// ou file://, envoi en flux
    if (!success) { /* réessayer, notifier l'utilisateur... */ }
}

host.uploadMusic(file) { success -> }   // surcharge pour un java.io.File
```

| Contrainte | Valeur |
|---|---|
| Formats acceptés | `.mp3` `.flac` `.wav` `.ogg` `.aac` `.m4a` `.opus` |
| Taille maximale | 200 Mo |

---

## 7. Référence des méthodes

### `ArcadeShare` (objet)

| Méthode | Description | Accès |
|---|---|---|
| `init(context, appId)` | Initialise le SDK (une seule fois) | Tous |
| `init(context, appId, serverUrl, httpUrl)` | Initialise avec un serveur personnalisé (tests) | Tous |
| `getDeviceSerial()` | Identifiant stable de l'appareil | Tous |
| `createHost(username)` | Crée une instance hôte (une par room) | Hôte |
| `getHost(code)` / `getHosts()` | Retrouve le host d'une room / tous les hosts | Hôte |
| `rejoinHost(username, code)` | Réactive une room (sans effet si déjà gérée) | Hôte |
| `restoreRooms(username, codes)` | Réactive plusieurs rooms (idempotent, respecte `stopSharing`) | Hôte |
| `startSharing(username, codes)` / `stopSharing()` | Active / coupe le partage de toutes les rooms | Hôte |
| `isSharing` / `isConnecting` | État du partage (propriétés) | Hôte |
| `onSharingChanged` | Callback appelé quand l'état du partage change | Hôte |
| `shareTrack(key, uri)` | Envoie la piste courante à toutes les rooms (dédupliqué) | Hôte |
| `broadcastState(state)` | Diffuse l'état de lecture à toutes les rooms | Hôte |
| `fetchMyRooms(onResult)` | Liste les rooms permanentes de l'appareil | Hôte |
| `deleteRoom(code, onResult)` | Supprime une room côté serveur | Hôte |
| `createGuest(username)` | Crée une instance invité | Invité |
| `release()` | Coupe toutes les connexions (les rooms sont conservées) | Tous |
| `version()` | Version du SDK | Tous |

### `ArcadeShareHost`

| Membre | Description |
|---|---|
| `setCallback(cb)` | Définit (ou retire, avec `null`) le callback d'événements |
| `createRoom()` | Crée une nouvelle room |
| `rejoinRoom(code)` | Réactive une room existante |
| `resume()` | Relance une room arrêtée avec `stopBroadcasting()` |
| `updateState(state)` | Envoie l'état de lecture à cette room |
| `shareTrack(key, uri)` | Envoie la piste à cette room (dédupliqué) |
| `uploadMusic(uri \| file, onResult)` | Envoi manuel d'un fichier audio |
| `stopBroadcasting()` | Arrête la diffusion sans supprimer la room |
| `deleteRoom()` | Supprime la room (préférer `ArcadeShare.deleteRoom(code)`) |
| `release()` | Libère l'instance |
| `room` / `roomCode` / `isActive` | Room courante, son code, connexion en cours ou établie |

### `ArcadeShareGuest`

| Méthode | Description |
|---|---|
| `setCallback(cb)` | Définit le callback d'événements |
| `joinRoom(code)` | Rejoint une room |
| `leaveRoom()` | Quitte la room (room conservée) |
| `getStreamUrl()` | URL du flux audio de la room courante |
| `release()` | Libère l'instance |

---

## 8. Référence des événements

### `HostCallback`

| Méthode | Paramètres | Déclenché quand |
|---|---|---|
| `onRoomCreated` | `RoomCode` | Room créée ou réactivée (aussi après une reconnexion) |
| `onGuestJoined` | `RoomCode, String username` | Un invité rejoint |
| `onGuestLeft` | `RoomCode, String username` | Un invité quitte |
| `onError` | `String message` | Erreur (room pleine, appId refusé, connexion impossible...) |
| `onDisconnected` | — | Connexion perdue |

### `GuestCallback`

| Méthode | Paramètres | Déclenché quand |
|---|---|---|
| `onRoomJoined` | `String code, String hostName, Int guestCount, ShareState?` | Connexion à la room réussie |
| `onStateReceived` | `ShareState` | Nouvel état reçu de l'hôte |
| `onHostLeft` | — | L'hôte a quitté ou est déconnecté |
| `onError` | `String message` | Erreur reçue du serveur (room introuvable, room pleine...) |
| `onDisconnected` | — | Connexion perdue |

### Modèle `RoomCode`

| Champ | Type | Description |
|---|---|---|
| `code` | `String` | Code de la room (6 caractères) |
| `guestCount` | `Int` | Nombre d'invités connectés (0 par défaut) |
| `maxGuests` | `Int` | Capacité de la room (6) |
| `isFull` | `Boolean` | Vrai si la room est pleine |

### Modèle `ShareState`

| Champ | Type | Description |
|---|---|---|
| `title` | `String` | Titre de la piste |
| `artist` | `String` | Artiste |
| `artworkUrl` | `String` | URL de la pochette (optionnel) |
| `audioUrl` | `String` | URL du flux audio (vide tant qu'aucune musique n'est diffusée) |
| `currentFile` | `String` | Nom du fichier côté serveur (détection changement de piste) |
| `position` | `Long` | Position en millisecondes, déjà corrigée de la latence réseau |
| `duration` | `Long` | Durée totale en millisecondes |
| `isPlaying` | `Boolean` | Lecture en cours côté hôte |
| `nextTitle` | `String` | Titre suivant (optionnel) |
| `timestamp` | `Long` | Horodatage Unix ms |

---

<sub>ArcadeShare SDK · Android · Audio_Arcade — La_TNT<br>Copyright © 2024–2026 Audio_Arcade. Tous droits réservés.</sub>
