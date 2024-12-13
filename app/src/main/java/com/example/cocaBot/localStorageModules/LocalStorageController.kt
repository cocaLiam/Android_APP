package com.example.cocaBot.localStorageModules

// Android 기본 패키지
import android.content.Context
import android.content.SharedPreferences



class LocalStorageController(context: Context) {
    private val fileName = "LocalStorage"
    // 로컬 저장소 접근 객체 생성
    private val sharedPreferences:SharedPreferences = context.getSharedPreferences(
        fileName, Context.MODE_PRIVATE
    )

    fun saveLocalStorage(){
        // 로컬 저장소에 데이터 저장
        with(sharedPreferences.edit()) {
            putString("userId", "aaa")
            putBoolean("isLoggedIn", true) // 로그인 상태 저장
            apply() // 비동기적으로 저장
        }
    }

    fun loadLocalStorage(){
        // 로컬 저장소에서 데이터 불러오기
        val isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false) // Boolean 값 불러오기
        val userId = sharedPreferences.getString("userId",null) // 문자열 값 불러오기
    }
}
