package com.fluxawallpapers.app.data.recommendation

import android.content.Context
import com.fluxawallpapers.app.util.FluxaLog
import com.google.firebase.Timestamp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val analytics = FirebaseAnalytics.getInstance(context)

    suspend fun ensureAuthenticated(): String? {
        val currentUser = auth.currentUser
        if (currentUser != null) return currentUser.uid

        return try {
            val result = auth.signInAnonymously().await()
            val uid = result.user?.uid
            if (uid != null) {
                initializeUserProfile(uid)
            }
            uid
        } catch (e: Exception) {
            FluxaLog.e("Firebase anonymous auth failed: ${e.message}")
            null
        }
    }

    private suspend fun initializeUserProfile(uid: String) {
        val userProfile = hashMapOf(
            "created_at" to Timestamp.now(),
            "last_active" to Timestamp.now(),
            "state" to "active",
            "recommendation_version" to 1
        )
        try {
            db.collection("users").document(uid)
                .set(userProfile, SetOptions.merge())
                .await()
            FluxaLog.d("Firebase user profile initialized for $uid")
        } catch (e: Exception) {
            FluxaLog.e("Failed to initialize Firebase user profile: ${e.message}")
        }
    }

    suspend fun updateHeartbeat() {
        val uid = ensureAuthenticated() ?: return
        try {
            db.collection("users").document(uid)
                .update("last_active", Timestamp.now())
                .await()
        } catch (e: Exception) {
            FluxaLog.e("Failed to update heartbeat: ${e.message}")
        }
    }

    suspend fun logInteraction(
        wallpaperId: String,
        source: String,
        action: String,
        tags: List<String>,
        searchQuery: String? = null
    ) {
        val uid = ensureAuthenticated() ?: return
        val interactionId = UUID.randomUUID().toString()
        
        val event = hashMapOf(
            "interaction_id" to interactionId,
            "device_id" to uid,
            "wallpaper_id" to wallpaperId,
            "source" to source,
            "action" to action,
            "tags" to tags,
            "search_query" to searchQuery,
            "timestamp" to Timestamp.now()
        )

        try {
            // 1. Log the event
            db.collection("users").document(uid)
                .collection("events")
                .document(interactionId)
                .set(event)
                .await()

            // 2. Log the event to Firebase Analytics
            val bundle = android.os.Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_ID, wallpaperId)
                putString(FirebaseAnalytics.Param.CONTENT_TYPE, "wallpaper")
                putString("source", source)
                putString("action", action)
                putString("tags", tags.joinToString(","))
                if (searchQuery != null) {
                    putString(FirebaseAnalytics.Param.SEARCH_TERM, searchQuery)
                }
            }
            analytics.logEvent("wallpaper_interaction", bundle)

            // 3. Update preference scores atomically
            val updates = mutableMapOf<String, Any>()
            val weight = getActionWeight(action)
            
            tags.forEach { tag ->
                val normalizedTag = tag.trim().lowercase()
                if (normalizedTag.isNotEmpty()) {
                    updates[normalizedTag] = FieldValue.increment(weight.toDouble())
                }
            }

            if (updates.isNotEmpty()) {
                db.collection("users").document(uid)
                    .collection("preferences")
                    .document("scores")
                    .set(updates, SetOptions.merge())
                    .await()
            }
            
            FluxaLog.d("Logged interaction: $action for $wallpaperId")
        } catch (e: Exception) {
            FluxaLog.e("Failed to log interaction: ${e.message}")
        }
    }

    private fun getActionWeight(action: String): Float {
        // Matches WallpaperAction.name (as passed from WallpaperRepository.recordWallpaperAction),
        // plus a few legacy/alternate aliases for backward compatibility.
        return when (action.lowercase()) {
            "set", "set_wallpaper" -> 3.0f
            "save", "pin" -> 2.0f
            "view_long", "view", "click" -> 1.0f
            "long_press" -> 0.5f
            "search" -> 1.5f
            "skip", "dismiss" -> -2.0f
            "rapid_skip" -> -1.0f
            else -> 0.0f
        }
    }

    suspend fun getTopTags(limit: Int = 5): List<String> {
        val uid = ensureAuthenticated() ?: return emptyList()
        return try {
            val doc = db.collection("users").document(uid)
                .collection("preferences")
                .document("scores")
                .get()
                .await()
            
            if (!doc.exists()) return emptyList()

            val scores = doc.data ?: return emptyList()
            scores.mapNotNull { (tag, score) ->
                if (score is Number) tag to score.toDouble() else null
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
        } catch (e: Exception) {
            FluxaLog.e("Failed to get top tags: ${e.message}")
            emptyList()
        }
    }

    /**
     * Like [getTopTags] but preserves the actual score for each tag, so callers can weight
     * Firebase-derived preferences against local ones instead of treating every returned tag
     * as equally strong. Only returns tags with a positive score (negative-weighted / disliked
     * tags should never be used to seek out MORE of that content).
     */
    suspend fun getTopTagsWithScores(limit: Int = 10): List<Pair<String, Double>> {
        val uid = ensureAuthenticated() ?: return emptyList()
        return try {
            val doc = db.collection("users").document(uid)
                .collection("preferences")
                .document("scores")
                .get()
                .await()

            if (!doc.exists()) return emptyList()

            val scores = doc.data ?: return emptyList()
            scores.mapNotNull { (tag, score) ->
                if (score is Number && score.toDouble() > 0.0) tag to score.toDouble() else null
            }
            .sortedByDescending { it.second }
            .take(limit)
        } catch (e: Exception) {
            FluxaLog.e("Failed to get top tags with scores: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchWallpapersByTagPrefix(prefix: String, limit: Long = 20): List<Map<String, Any>> {
        val clean = prefix.trim().lowercase()
        if (clean.isEmpty()) return emptyList()
        return try {
            val snapshot = db.collection("wallpapers")
                .whereArrayContains("tags", clean)
                .limit(limit)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            FluxaLog.e("Failed to search Firestore wallpapers by tag prefix: ${e.message}")
            emptyList()
        }
    }
}
