package com.example.cocaBot.services
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

/*
* 둘다 BackGround 용도의 Service 이긴 하나
* 일반 Service 는 APP 실행중 지속적으로 백그라운드에서 사용되는 경우가 적합하고
*   - 예시 : 음악 재생, 실시간 데이터 동기화, 위치 추적 등 ...
* Job Service 는 APP의 특정 조건(꺼질때, 켜질때, 충전시작할때, 등등) 백그라운드에서 사용되는 경우가 적합함.
*   - 예시 : 앱 시작/종료 시 데이터 백업, 배터리 충전 시 업데이트 수행, 네트워크 연결 시 데이터 동기화 등 ...
* */
class BackGroundService : Service() {

    // Binder 인스턴스 생성
    private val binder = LocalBinder()

    // Binder 클래스 정의
    inner class LocalBinder : Binder() {
        fun getService(): BackGroundService = this@BackGroundService
    }

    // Service가 처음 생성될 때 호출
    override fun onCreate() {
        super.onCreate()
        Log.d("Service", "onCreate")
    }

    // Service에 바인드할 때 호출되며, Binder 객체를 반환
    override fun onBind(intent: Intent): IBinder {
        Log.d("Service", "onBind")
        return binder
    }

    // Service가 언바인드될 때 호출
    override fun onUnbind(intent: Intent): Boolean {
        Log.d("Service", "onUnbind")
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent) {
        // onUnbind()가 호출된적이 있는 상태에서, 다시 bindService()를 통해 바인딩 할 때
    }

    // startService()로 Service가 시작될 때 호출
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("Service", "onStartCommand")
        // START_STICKY: Service가 강제 종료되면 재시작
        // START_NOT_STICKY: Service가 강제 종료되면 재시작하지 않음
        // START_REDELIVER_INTENT: Service가 강제 종료되면 마지막 Intent와 함께 재시작
        return START_STICKY
    }

    // Service가 종료될 때 호출
    override fun onDestroy() {
        super.onDestroy()
        Log.d("Service", "onDestroy")
    }

    // 실제 작업을 수행할 함수
    fun performTask() {
        // 여기에 실제 작업 구현
    }
}
