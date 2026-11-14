package com.azzam.sadeeqi

import com.azzam.sadeeqi.memory.EncryptedMemory
import org.json.JSONObject

data class LocalProfile(
    val fullName: String = "عزام فايز",
    val preferredName: String = "عزام",
    val birthDate: String = "15/09/2006",
    val primaryLanguage: String = "العربية الفصحى"
)

class LocalProfileStore(private val memory: EncryptedMemory) {
    companion object {
        private const val KEY = "profile:owner"
    }

    fun load(): LocalProfile {
        val saved = memory.search(KEY).firstOrNull()?.second ?: return LocalProfile()
        return runCatching {
            val json = JSONObject(saved)
            LocalProfile(
                fullName = json.optString("fullName", "عزام فايز"),
                preferredName = json.optString("preferredName", "عزام"),
                birthDate = json.optString("birthDate", "15/09/2006"),
                primaryLanguage = json.optString("primaryLanguage", "العربية الفصحى")
            )
        }.getOrDefault(LocalProfile())
    }

    fun save(profile: LocalProfile) {
        require(profile.fullName.isNotBlank()) { "يرجى إدخال الاسم الكامل." }
        require(profile.preferredName.isNotBlank()) { "يرجى إدخال الاسم المفضل." }
        val json = JSONObject()
            .put("fullName", profile.fullName.trim())
            .put("preferredName", profile.preferredName.trim())
            .put("birthDate", profile.birthDate.trim())
            .put("primaryLanguage", profile.primaryLanguage.trim())
            .toString()
        memory.save(KEY, "profile", json)
    }
}
