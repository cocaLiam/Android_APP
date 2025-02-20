package com.example.cocaBot.services
import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.example.cocaBot.MainActivity

/*
* 둘다 BackGround 용도의 Service 이긴 하나
* 일반 Service 는 APP 실행중 지속적으로 백그라운드에서 사용되는 경우가 적합하고
*   - 예시 : 음악 재생, 실시간 데이터 동기화, 위치 추적 등 ...
* Job Service 는 APP의 특정 조건(꺼질때, 켜질때, 충전시작할때, 등등) 백그라운드에서 사용되는 경우가 적합함.
*   - 예시 : 앱 시작/종료 시 데이터 백업, 배터리 충전 시 업데이트 수행, 네트워크 연결 시 데이터 동기화 등 ...
* */
class OnResumeJobService : JobService() {
    companion object {
        const val JOB_ID = 111
        val jobServiceLogTag = " - OnResumeJobService"
        private var mainActivity: MainActivity? = null

        fun setMainActivity(activity: MainActivity?) {
            mainActivity = activity
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        mainActivity?.jobServiceOnResume()
        setMainActivity(null)     // 작업 완료 후 참조 제거

        return false
//        jobFinished(params, false)
//        return true
////        true: 작업이 아직 진행 중(비동기 작업)이며, 작업이 완료되면 직접 jobFinished()를 호출하겠다는 의미
////        false: 작업이 이미 완료되었으며, 시스템이 자동으로 job을 종료해도 된다는 의미
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
//        true: 작업이 중단되었을 때 나중에 이 job을 다시 실행해달라는 의미 (재스케줄링)
//        false: 작업이 중단되어도 다시 실행할 필요가 없다는 의미
    }
}
