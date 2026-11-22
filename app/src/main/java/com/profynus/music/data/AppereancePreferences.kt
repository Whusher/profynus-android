package com.profynus.music.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profynusPreferences by preferencesDataStore(name = "profynus_appearance")

enum class ShapeProfile(val label: String, val deformation: Float, val frequency: Int) {
    SOFT("Soft", 0.035f, 3),
    AGGRESSIVE("Aggressive", 0.14f, 9),
    EDM("EDM", 0.10f, 6),
    RELAX("Relax", 0.012f, 2)
}

enum class PlayerVisual(val label: String) {
    SPHERE("3D sphere"),
    ALBUM_ART("Album art"),
}

data class AppearanceSettings(
    val paletteId: String = "profynus_cyan",
    val shape: ShapeProfile = ShapeProfile.SOFT,
    val reducedMotion: Boolean = false,
    val playerVisual: PlayerVisual = PlayerVisual.SPHERE,
)

class AppearancePreferences(context: Context) {
    private val appContext = context.applicationContext

    private companion object {
        val PALETTE = stringPreferencesKey("palette_id")
        val SHAPE = stringPreferencesKey("shape_profile")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val PLAYER_VISUAL = stringPreferencesKey("player_visual")
    }

    val settings: Flow<AppearanceSettings> = appContext.profynusPreferences.data.map { preferences ->
        AppearanceSettings(
            paletteId = preferences[PALETTE] ?: "profynus_cyan",
            shape = ShapeProfile.entries.firstOrNull {
                it.name == preferences[SHAPE]
            } ?: ShapeProfile.SOFT,
            reducedMotion = preferences[REDUCED_MOTION] ?: false,
            playerVisual = PlayerVisual.entries.firstOrNull {
                it.name == preferences[PLAYER_VISUAL]
            } ?: PlayerVisual.SPHERE,
        )
    }

    suspend fun selectPalette(id: String) {
        appContext.profynusPreferences.edit { it[PALETTE] = id }
    }

    suspend fun selectShape(profile: ShapeProfile) {
        appContext.profynusPreferences.edit { it[SHAPE] = profile.name }
    }

    suspend fun selectPlayerVisual(visual: PlayerVisual) {
        appContext.profynusPreferences.edit { it[PLAYER_VISUAL] = visual.name }
    }

    suspend fun setReducedMotion(reduced: Boolean) {
        appContext.profynusPreferences.edit { it[REDUCED_MOTION] = reduced }
    }
}
