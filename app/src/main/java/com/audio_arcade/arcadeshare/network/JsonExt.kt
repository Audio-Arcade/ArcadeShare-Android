//Copyright © 2024-2026 Audio_Arcade. Tous droits réservés.
package com.audio_arcade.arcadeshare.network

import org.json.JSONObject

/** Texte d'un champ, ou null s'il est absent, `null` en JSON, ou vide. */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }