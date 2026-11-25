# Audio_Arcade_SDK

[![](https://jitpack.io/v/Audio-Arcade/ArcadeShare-Android.svg)](https://jitpack.io/#Audio-Arcade/ArcadeShare-Android)

## Arcade Share

> Partagez votre musique à distance, en temps réel, sans réseau commun — et gardez le contrôle total de votre session.

📱 **Android** &nbsp;·&nbsp; 🪟 **Windows** &nbsp;·&nbsp; 🐧 ~~Linux~~ *(à venir)*

**Version 4.0.0** &nbsp;·&nbsp; [📖 Documentation en ligne](https://audio-arcade.fr.eu.org/sdk)

---

## 📋 Présentation

**Arcade Share** est un SDK qui intègre la fonctionnalité de *cast amélioré* d'Audio_Arcade directement dans vos applications musicales. Il permet le partage de musique à distance, de façon synchronisée, **sans que les auditeurs aient besoin d'être sur la même connexion internet**. Vous restez le maître de la session : les auditeurs ne peuvent que mettre en pause ou reprendre la lecture de leur côté uniquement.

> ℹ️ **À noter :** le code de session fonctionne uniquement **de vous vers un autre appareil**. Il n'est pas possible d'écouter votre propre musique via ce code sur le même compte — le partage est conçu pour être partagé avec d'autres personnes, pas avec vous-même.

---

## ✨ Fonctionnalités clés

| | |
|---|---|
| 🛰️ **Sans réseau commun** | Hôte et auditeurs n'ont pas besoin de partager la même connexion. Le SDK gère tout automatiquement. |
| 🔄 **Synchronisation temps réel** | Tout le monde écoute la même chose au même moment, grâce à la synchronisation côté serveur. |
| 👑 **Contrôle maître** | L'hôte garde la main sur tout. Les auditeurs peuvent uniquement pause / reprendre de leur côté. |
| 🔗 **Compte lié et pseudo vérifié** *(V4)* | L'utilisateur lie son appareil à son compte Audio_Arcade : son pseudo réservé devient un pseudo **vérifié** et ne peut plus être usurpé. |
| ✅ **Certification** | Ton application peut être certifiée par Audio_Arcade, et le SDK expose ce statut. |

---

## 🔐 Permissions par rôle

| Rôle | Pause / Reprise | Changer de piste | File d'attente | Volume | Stopper la session | Quitter la session |
|---|---|---|---|---|---|---|
| 👑 Hôte | ✅ Oui | ✅ Oui | ✅ Oui | ✅ Oui | ✅ Oui | ❌ Non |
| 🎧 Auditeur | ⚠️ Local uniquement | ❌ Non | ❌ Non | ✅ Oui | ❌ Non | ✅ Oui |

---

## 🖥️ Plateformes supportées

| Plateforme | Statut |
|---|---|
| 📱 Android | ✅ Disponible |
| 🪟 Windows | ✅ Disponible |
| 🐧 Linux | 🚧 En développement |

---

## 🛡️ Sécurité & Certification

> 🚨 **Ne partagez jamais votre code de session avec une application non certifiée**
>
> Ne saisissez ou ne partagez jamais un code de lecture dans une application qui n'a pas obtenu la certification officielle Audio_Arcade. Une application non certifiée peut intercepter votre session, accéder à vos données ou compromettre votre compte. **Vérifiez toujours la certification avant d'utiliser le SDK dans une application tierce.** Si une application vous demande ce code sans être certifiée, refusez et signalez-la immédiatement.

**[✅ Voir les applications certifiées](https://audio-arcade.odoo.com/sdk-doc/arcade-share-server/certified-apps)**

> 🔎 **Depuis la V3, le SDK expose la certification.** Ton application peut savoir si elle est elle-même certifiée, consulter le statut public d'une application à partir de son nom et, côté invité, savoir si l'application de l'hôte est certifiée. Rien n'est bloqué : c'est une information à afficher à l'utilisateur (voir [section 8](#8-certification-de-lapplication)).

> 🔗 **Depuis la V4, le SDK protège les pseudos.** Un pseudo réservé sur Audio_Arcade ne peut être utilisé dans une room que par son propriétaire, **vérifié** grâce à la liaison de son compte. Toute autre personne reçoit l'erreur « Nom réservé » (voir [section 7](#7-compte-audio_arcade-lié-pseudo-vérifié)).

---

# 📱 Documentation Android — Kotlin

> SDK de synchronisation audio en temps réel entre appareils. L'URL du serveur est intégrée au SDK : ton application n'a plus qu'à fournir son `appId`.

---

## 📑 Sommaire

1. [Authentification (appId)](#-authentification-appid)
2. [Installation](#1-installation)
3. [ID de l'appareil](#2-id-de-lappareil)
4. [Mode Hôte — Cast](#3-mode-hôte--cast)
5. [Rooms permanentes](#4-rooms-permanentes)
6. [Mode Invité — Live](#5-mode-invité--live)
7. [Envoi de musique](#6-envoi-de-musique)
8. [Compte Audio_Arcade lié (pseudo vérifié)](#7-compte-audio_arcade-lié-pseudo-vérifié)
9. [Certification de l'application](#8-certification-de-lapplication)
10. [Référence des méthodes](#9-référence-des-méthodes)
11. [Référence des événements](#10-référence-des-événements)
12. [Migration depuis la V3 et historique](#11-migration-depuis-la-v3-et-historique)

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
| Application révoquée en cours de session | `onError` avec le motif, sans nouvelle tentative de reconnexion |
| Version du SDK dépréciée (fonctionne encore) | Avertissement dans le logcat (tag `ArcadeShare`) |
| Version du SDK inconnue | Connexion refusée : télécharge la dernière version du SDK |
| Application non certifiée | Aucun blocage : `ArcadeShare.isCertified` vaut `false` (voir section 8) |

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
    implementation("com.github.Audio-Arcade:ArcadeShare-Android:4.0.0")
}
```

> ℹ️ **Android 6.0 (API 23) minimum** : le jeton du compte lié (section 7) est chiffré avec le Keystore Android. Vérifie que ton module déclare `minSdk = 23` ou plus.

### Option B — Fichier AAR

**[⬇ Télécharger arcadeshare-4.0.0.aar](https://github.com/Audio-Arcade/ArcadeShare-Android/releases/download/4.0.0/arcadeshare-4.0.0.aar)**

Copie le fichier dans `app/libs/` et déclare-le avec sa dépendance (OkHttp n'est pas embarqué dans un AAR) :

```kotlin
dependencies {
    implementation(files("libs/arcadeshare-4.0.0.aar"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

> ⚠️ N'utilise pas en même temps la dépendance Gradle **et** le fichier AAR : le SDK serait présent en double, et la compilation échouerait (classes dupliquées) ou le comportement serait imprévisible. Choisis l'une des deux options.

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

> ℹ️ Identifiant stable de l'appareil. Comme sur Windows, c'est lui qui donne accès aux rooms permanentes. Il peut changer si l'application est signée avec une autre clé (build debug et release) ou après une réinitialisation d'usine. Pour les pseudos réservés, la méthode recommandée depuis la V4 est la **liaison de compte** (section 7).

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

> ℹ️ Une room n'est réellement prête qu'une fois `onRoomCreated` reçu (voir `HostCallback`, section 4). Un refus (pseudo réservé, limite de rooms atteinte…) arrive par `onError`.

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

> ℹ️ **Reconnexion** : après une coupure réseau, chaque room se reconnecte automatiquement pendant environ 5 minutes. Si elle n'y parvient pas, la connexion est abandonnée et `onError` est appelé. Inutile de gérer la reconnexion toi-même dans `onDisconnected`.

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
    override fun onError(message: String) {
        // refus : « Nom réservé », « Maximum 2 rooms permanentes atteint », appId refusé…
    }
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

> ⚠️ **`joinRoom` n'attend pas la réponse du serveur** : il envoie seulement la demande. L'entrée est confirmée par `onRoomJoined`. Un refus (code inconnu, room pleine, pseudo réservé…) arrive par `onError`, **avant** toute confirmation. Si tu ne gères pas ce cas, ton application se croit « dans une room » qui n'existe pas. Considère donc l'utilisateur dans la room **uniquement après `onRoomJoined`**, comme ci-dessous.

```kotlin
val guest = ArcadeShare.createGuest(username = "Mon Pseudo")
var joined = false   // vrai seulement après la confirmation d'entrée

guest.setCallback(object : GuestCallback {
    override fun onRoomJoined(code: String, hostName: String, guestCount: Int, lastState: ShareState?) {
        joined = true   // entrée confirmée par le serveur : connecté à la room
    }
    override fun onStateReceived(state: ShareState) {
        // mettre à jour l'UI + synchroniser la lecture
    }
    override fun onHostLeft() {
        // l'hôte a quitté la session
    }
    override fun onHostCertification(certified: Boolean?) {
        // application de l'hôte certifiée (true), non certifiée (false) ou inconnue (null)
        // appelé juste avant onRoomJoined — informatif uniquement
    }
    override fun onHostHandle(handle: String?) {
        // pseudo vérifié de l'hôte (compte Audio_Arcade lié), ou null — V4
        // appelé juste avant onRoomJoined
    }
    override fun onError(message: String) {
        if (!joined) {
            // Entrée refusée (code inconnu, room pleine, pseudo réservé…) : on n'est PAS dans la room
            guest.release()
            // affiche message à l'utilisateur et réactive tes boutons
            return
        }
        // erreur survenue dans la room
    }
    override fun onDisconnected() { }
})

guest.joinRoom("ABC123")
```

> ℹ️ Après une coupure réseau, le SDK se reconnecte et rejoint à nouveau la room tout seul : `onRoomJoined` est alors rappelé. `onDisconnected` n'est appelé que lorsque la reconnexion est abandonnée.

### Lecture audio

Le SDK synchronise les métadonnées et l'état de lecture, mais ne joue pas le son lui-même : c'est à ton application de consommer le flux et de piloter un lecteur (ex. Media3/ExoPlayer). Une fois qu'un `ShareState` reçu contient un `currentFile` non vide, son champ `audioUrl` pointe vers le flux HTTP de la room :

```kotlin
override fun onStateReceived(state: ShareState) {
    if (state.audioUrl.isEmpty()) return   // pas encore de musique côté hôte

    if (state.currentFile != loadedFile) {
        loadedFile = state.currentFile
        // audioUrl contient déjà l'authentification et le nom du fichier courant :
        // utilise-la telle quelle (le lecteur ne resservira jamais l'ancien fichier pour une nouvelle piste)
        player.setMediaItem(MediaItem.fromUri(state.audioUrl))
        player.prepare()
    }

    // state.position / state.duration sont en millisecondes, déjà corrigés
    // de la latence réseau : resynchronise si l'écart dépasse ~3 secondes.
}
```

> ⚠️ Depuis la V3, `audioUrl` contient déjà ses paramètres (`?appId=…&versionId=…&f=…`). N'ajoute plus `?f=` toi-même : l'URL deviendrait invalide.

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

> ℹ️ Depuis la V3, `onResult` reçoit `false` après 3 tentatives infructueuses, et le SDK vérifie que le serveur sert bien le fichier avant de considérer la piste comme envoyée. Quand le serveur refuse explicitement le fichier (trop volumineux, format non pris en charge, application non autorisée), la raison est transmise à `onError`.

---

## 7. Compte Audio_Arcade lié (pseudo vérifié)

*Nouveau en V4.* L'utilisateur peut lier son appareil à son **compte Audio_Arcade**. Son **pseudo réservé** devient alors un pseudo **vérifié** : il apparaît comme tel chez l'hôte, et **personne d'autre ne peut l'utiliser**.

### Principe

| Étape | Qui | Action |
|---|---|---|
| 1 | L'utilisateur | Se connecte avec Discord sur la page [Mon compte](https://audio-arcade.fr.eu.org/compte), réserve son pseudo, puis clique sur **Lier une application** : un code s'affiche (valable **10 minutes**, à usage unique). |
| 2 | Ton application | Envoie ce code au SDK avec `ArcadeShare.linkAccount`. Le SDK obtient un **jeton secret** et le stocke chiffré sur l'appareil. |
| 3 | Le SDK | Vérifie le jeton **automatiquement** à chaque connexion, avant de créer ou de rejoindre une room. |
| 4 | L'utilisateur | Peut révoquer l'application à tout moment depuis **Applications connectées** sur son compte. À la connexion suivante, le SDK supprime le jeton local (`onLinkRevoked`). |

**Règle de protection** : un pseudo réservé ne peut être utilisé dans une room que par son propriétaire vérifié. Toute autre personne reçoit l'erreur `Nom réservé`. Les comptes invités du site ne peuvent pas lier d'application (ils n'ont pas de pseudo réservé).

> ℹ️ Le jeton est lié à **ton application** et à **l'appareil** : volé, il est inutilisable ailleurs. Chaque appareil et chaque application a sa propre liaison, visible et révocable séparément (par exemple « Windows » et « Android » pour la même application).

### Rien à implémenter pour le stockage

Contrairement à Windows, **tu n'as aucun stockage à fournir** : le SDK chiffre le jeton avec une clé du Keystore Android (non exportable) et le range dans ses propres préférences. Si le jeton devient illisible (sauvegarde restaurée sur un autre appareil, clé supprimée…), il est ignoré, comme si aucune liaison n'existait. Il n'est jamais journalisé.

### 1. Lier l'appareil

À appeler depuis tes paramètres, avec le code saisi par l'utilisateur (après `ArcadeShare.init`) :

```kotlin
ArcadeShare.linkAccount(codeSaisi) { ok, handle, error ->   // appelé sur le thread principal
    if (ok) {
        // lié : handle = pseudo vérifié (null si l'utilisateur n'a pas encore réservé de pseudo)
    } else {
        // error : « Code invalide ou expiré », « Le serveur n'a pas répondu. », …
    }
}
```

> ℹ️ `linkAccount` ouvre sa propre connexion temporaire : tu n'as pas besoin d'être dans une room, ni d'avoir démarré le partage.

### 2. Utiliser le pseudo vérifié

Une fois le compte lié, **la vérification est automatique** : le SDK vérifie le jeton juste après l'authentification, avant d'envoyer `create_room`, `rejoin_room` ou `join_room` — pour l'hôte comme pour l'invité, y compris après une reconnexion automatique. Un serveur injoignable ou muet n'empêche jamais de continuer (délai maximum : 8 secondes).

```kotlin
ArcadeShare.hasStoredLink    // un jeton est enregistré sur l'appareil (pas forcément encore vérifié)
ArcadeShare.isLinked         // vrai après une vérification réussie (liaison ou connexion à une room)
ArcadeShare.linkedHandle     // pseudo réservé vérifié, ou null
ArcadeShare.linkedName       // nom du compte lié

ArcadeShare.onLinkRevoked = {   // thread principal
    // l'utilisateur a révoqué l'application : le jeton local est supprimé
}
// Remets ArcadeShare.onLinkRevoked = null quand l'écran se ferme

ArcadeShare.unlink()         // oublie la liaison sur cet appareil
```

> ℹ️ `isLinked`, `linkedHandle` et `linkedName` ne sont renseignés qu'**après** une vérification réussie sur la connexion en cours. Pour afficher l'état dans tes paramètres avant toute connexion, utilise `hasStoredLink` et mémorise toi-même le dernier pseudo vérifié.

Côté invité, le pseudo vérifié de l'hôte est fourni par `GuestCallback.onHostHandle` (section 5).

### 3. Imposer le pseudo dans ton interface

**Recommandation** : quand un compte est lié et possède un pseudo réservé, utilise ce pseudo à la place de la saisie libre, et verrouille le champ. Le serveur refuse de toute façon un pseudo réservé à qui n'en est pas propriétaire : le verrouillage n'est qu'un confort, la protection est côté serveur.

> ℹ️ La comparaison des pseudos ignore les majuscules, les accents et les séparateurs : `La_TNT`, `la-tnt` et `latnt` désignent le même pseudo.

### Plateforme déclarée

Chaque liaison indique sa plateforme, affichée dans **Applications connectées** : le SDK Android déclare automatiquement « Android ». Rien à configurer.

### Récapitulatif

| Élément | Rôle |
|---|---|
| `ArcadeShare.linkAccount(code, onResult)` | Lie l'appareil avec le code saisi par l'utilisateur |
| `ArcadeShare.unlink()` | Oublie la liaison sur cet appareil (pour la révoquer côté serveur : « Applications connectées » du compte) |
| `ArcadeShare.hasStoredLink` | Un jeton est enregistré sur l'appareil |
| `ArcadeShare.isLinked` · `linkedHandle` · `linkedName` | État de la liaison, après vérification |
| `ArcadeShare.onLinkRevoked` | Liaison révoquée par l'utilisateur : le jeton local a été supprimé |
| `GuestCallback.onHostHandle(handle)` | Pseudo vérifié de l'hôte, vu par l'invité |

---

## 8. Certification de l'application

Le serveur sait si ton application est certifiée par Audio_Arcade. Cette information est **purement informative** : une application non certifiée continue de fonctionner. Elle te permet d'afficher un indicateur (accueil, paramètres, page des codes…) pour que l'utilisateur sache à qui il confie son code de session.

### Certification de ta propre application

```kotlin
ArcadeShare.isCertified   // null = pas encore connu, true / false après la première connexion authentifiée

ArcadeShare.onCertificationChanged = { certified: Boolean? ->   // thread principal
    // mets à jour ton indicateur
}
// Remets ArcadeShare.onCertificationChanged = null quand l'écran se ferme
```

> ℹ️ `isCertified` n'est connu qu'après une connexion authentifiée (démarrage du partage ou jointure d'une room). Pour afficher le statut dès l'accueil, utilise `fetchCertificationStatus`.

### Statut public d'une application (par son nom)

```kotlin
ArcadeShare.fetchCertificationStatus("Audio_Arcade") { status ->   // thread principal
    // "certified" | "partial" | "unverified" | "revoked" | null (serveur injoignable)
}
```

| Statut | Signification |
|---|---|
| `certified` | Application certifiée par Audio_Arcade |
| `partial` | Certification partielle |
| `unverified` | Application non vérifiée / non certifiée |
| `revoked` | Certification révoquée |
| `null` | Statut indisponible (réseau ou serveur injoignable) |

> ℹ️ Cette requête est publique : elle ne nécessite ni connexion ni room, et ne révèle jamais l'`appId`.

### Certification de l'application de l'hôte (invité)

```kotlin
override fun onHostCertification(certified: Boolean?) {
    // true = certifiée, false = non certifiée, null = inconnue (serveur ou hôte non identifié)
    // appelé juste avant onRoomJoined
}
```

---

## 9. Référence des méthodes

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
| `linkAccount(code, onResult)` | Lie l'appareil à un compte Audio_Arcade avec le code saisi. `onResult(ok, handle, error)` sur le thread principal — **V4** | Tous |
| `unlink()` | Oublie la liaison sur cet appareil — **V4** | Tous |
| `hasStoredLink` | Un jeton de liaison est enregistré sur l'appareil (propriété) — **V4** | Tous |
| `isLinked` / `linkedHandle` / `linkedName` | État de la liaison, après vérification (propriétés) — **V4** | Tous |
| `onLinkRevoked` | Callback : liaison révoquée par l'utilisateur, jeton local supprimé — **V4** | Tous |
| `release()` | Coupe toutes les connexions (les rooms sont conservées) | Tous |
| `isCertified` / `onCertificationChanged` | Certification de ton application (`Boolean?`) et callback de changement | Tous |
| `fetchCertificationStatus(nom, onResult)` | Statut public d'une application d'après son nom | Tous |
| `version()` | Version du SDK (`"4.0.0"`) | Tous |

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
| `joinRoom(code)` | Envoie la demande d'entrée dans une room (confirmée par `onRoomJoined`) |
| `leaveRoom()` | Quitte la room (room conservée) |
| `getStreamUrl()` | URL du flux audio de la room courante |
| `release()` | Libère l'instance |

---

## 10. Référence des événements

### `HostCallback`

| Méthode | Paramètres | Déclenché quand |
|---|---|---|
| `onRoomCreated` | `RoomCode` | Room créée ou réactivée (aussi après une reconnexion) |
| `onGuestJoined` | `RoomCode, String username` | Un invité rejoint |
| `onGuestLeft` | `RoomCode, String username` | Un invité quitte |
| `onError` | `String message` | Erreur (room pleine, nom réservé, appId refusé, connexion impossible, fichier refusé à l'envoi...) |
| `onDisconnected` | — | Connexion perdue |

### `GuestCallback`

| Méthode | Paramètres | Déclenché quand |
|---|---|---|
| `onRoomJoined` | `String code, String hostName, Int guestCount, ShareState?` | Connexion à la room réussie — **c'est la confirmation d'entrée** |
| `onStateReceived` | `ShareState` | Nouvel état reçu de l'hôte |
| `onHostCertification` | `Boolean?` | Juste avant `onRoomJoined` : application de l'hôte certifiée (`true`), non certifiée (`false`) ou inconnue (`null`) |
| `onHostHandle` | `String?` | Juste avant `onRoomJoined` : pseudo vérifié de l'hôte (compte lié), ou `null` — **V4** |
| `onHostLeft` | — | L'hôte a quitté ou est déconnecté |
| `onError` | `String message` | Erreur reçue du serveur (room introuvable, room pleine, nom réservé...). Avant la confirmation d'entrée, elle signifie que l'entrée est **refusée** |
| `onDisconnected` | — | Connexion perdue, reconnexion abandonnée |

> ℹ️ `onHostCertification` et `onHostHandle` ont une implémentation par défaut (vide) : tes implémentations existantes de `GuestCallback` continuent de compiler sans changement.

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

## 11. Migration depuis la V3 et historique

### Migrer de la V3 vers la V4

**Rien ne casse** : une application compilée contre la V3 continue de fonctionner. Pour profiter des nouveautés :

1. Mets à jour la dépendance : `com.github.Audio-Arcade:ArcadeShare-Android:4.0.0` (ou le nouveau fichier AAR, en retirant l'ancien).
2. Vérifie que `minSdk` vaut **23 ou plus** (Keystore Android).
3. Pour les comptes liés et les pseudos vérifiés : ajoute un écran de liaison qui appelle `ArcadeShare.linkAccount` (section 7). Aucun stockage à implémenter.
4. Si ce n'est pas déjà fait, gère la **confirmation d'entrée** côté invité avec `onRoomJoined` / `onError` (section 5) : un code de room invalide ne doit pas laisser l'application « dans une room ».
5. Vérifie que ton projet ne référence **qu'une seule** version du SDK (Gradle ou AAR, pas les deux).

> ℹ️ Les anciennes versions restent acceptées par le serveur tant qu'elles ne sont pas retirées. Une version dépréciée fonctionne, avec un avertissement dans le logcat.

### Historique

| Version | Nouveautés |
|---|---|
| **4.0.0** | Compte Audio_Arcade lié et pseudo vérifié (`linkAccount`, `unlink`, `hasStoredLink`, `isLinked`, `linkedHandle`, `linkedName`, `onLinkRevoked`), jeton chiffré par le Keystore. Protection des pseudos réservés. Plateforme déclarée automatiquement. `GuestCallback.onHostHandle`. |
| **3.x** | Exposition de la certification (`isCertified`, `onCertificationChanged`, `fetchCertificationStatus`, `onHostCertification`). Vérification de l'envoi de la musique. `audioUrl` authentifiée. Reconnexion automatique. |

---

<sub>ArcadeShare SDK · Android · Audio_Arcade — La_TNT<br>Copyright © 2024–2026 Audio_Arcade. Tous droits réservés.</sub>
