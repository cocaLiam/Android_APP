package com.example.cocaBot.localStorageModules

// Android 기본 패키지
import android.content.Context

// 보안 패키지
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

    fun loadSecureStorage():String {
        // 데이터 읽기  , 실패시 "" 값 리턴
        return encryptedSharedPreferences.getString("accessToken", "") ?: ""
    }

    fun saveSecureStorage(tokenJsonStringObject: String):Boolean {
        // 데이터 저장
//        with(encryptedSharedPreferences.edit()) {
//            putString("accessToken", tokenJsonStringObject) // Access Token 저장
//            apply()
//        }
        val isSuccess = with(encryptedSharedPreferences.edit()) {
            putString("accessToken", tokenJsonStringObject)
            commit()  // apply() 대신 commit() 사용
        }
        return isSuccess
    }
}