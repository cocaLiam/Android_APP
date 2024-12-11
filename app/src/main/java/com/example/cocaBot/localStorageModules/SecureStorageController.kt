package com.example.cocaBot.localStorageModules

import android.content.Context

// 암호화
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

// 로컬 저장소 로그인 기능 << TODO: 자동로그인 사용여부 결정 필요
class SecureStorageController(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val fileName = "SecureStorage"

    // 로컬 저장소 접근 객체 생성
    val encryptedSharedPreferences = EncryptedSharedPreferences.create(
        fileName,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSecureStorage() {
        // 데이터 저장
        with(encryptedSharedPreferences.edit()) {
            putString("accessToken", "exampleAccessToken") // Access Token 저장
            putBoolean("isLoggedIn", true) // 로그인 상태 저장
            apply()
        }
    }

    fun loadSecureStorage() {
        // 데이터 읽기
        val accessToken = encryptedSharedPreferences.getString("accessToken", null)
        val isLoggedIn = encryptedSharedPreferences.getBoolean("isLoggedIn", false)

        if (isLoggedIn) {
            println("Access Token: $accessToken")
        } else {
            println("로그인이 필요합니다.")
        }
    }
}