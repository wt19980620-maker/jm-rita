package com.par9uet.jm.storage

import com.google.gson.reflect.TypeToken

class AdultContentConsentStorage(
    private val secureStorage: SecureStorage,
) {
    companion object {
        private const val STORAGE_KEY = "adultVideoContentAccepted"
    }

    fun isAccepted(): Boolean = secureStorage.get<Boolean>(
        STORAGE_KEY,
        object : TypeToken<Boolean>() {}.type,
    ) ?: false

    fun accept() {
        secureStorage.set(STORAGE_KEY, true)
    }
}

