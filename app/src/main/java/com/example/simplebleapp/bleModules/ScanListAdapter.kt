package com.example.simplebleapp.bleModules

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.simplebleapp.R

class ScanListAdapter() :
    RecyclerView.Adapter<ScanListAdapter.ScanViewHolder>() {
    private val devices = mutableListOf<BluetoothDevice>() // BLE 장치 목록을 저장하는 리스트
    private var selectedPosition = -1 // 선택된 항목의 인덱스를 저장하는 변수

    /* RecyclerView의 ViewHolder를 생성하는 메서드 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_list, parent, false) // item_scan_list 레이아웃을 inflate
        return ScanViewHolder(view) // ViewHolder 반환
    }

    /* RecyclerView의 각 항목에 데이터를 바인딩하는 메서드 */
    override fun onBindViewHolder(holder: ScanViewHolder, position: Int) {
        val device = devices[position] // 현재 위치의 BLE 장치 정보 가져오기
        holder.deviceName.text = device.name ?: "Unknown Device" // 장치 이름 설정 (없으면 "Unknown Device")
        holder.radioButton.isChecked = position == selectedPosition // 라디오 버튼 선택 상태 설정

        // 라디오 버튼 클릭 리스너
        holder.radioButton.setOnClickListener {
            // 선택된 항목의 인덱스를 업데이트
            val previousPosition = selectedPosition
            selectedPosition = holder.adapterPosition

            // 이전 선택 항목과 현재 선택 항목을 갱신
            notifyItemChanged(previousPosition) // 이전 선택 항목 갱신
            notifyItemChanged(selectedPosition) // 현재 선택 항목 갱신
        }
    }

//
//        holder.itemView.setOnClickListener {
//            selectedPosition = holder.adapterPosition
//            notifyDataSetChanged()
//        }

    /* RecyclerView의 항목 개수를 반환하는 메서드 */
    override fun getItemCount(): Int = devices.size

    /* 선택된 BLE 장치를 반환하는 메서드 */
    fun getSelectedDevice(): BluetoothDevice? {
        return if (selectedPosition != -1) devices[selectedPosition] else null// 선택된 장치가 없으면 반환
    }

    /* RecyclerView의 각 항목을 관리하는 ViewHolder 클래스 */
    class ScanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.device_name) // 장치 이름을 표시하는 TextView
        val radioButton: RadioButton = itemView.findViewById(R.id.radio_button) // 선택 상태를 표시하는 RadioButton
    }

    /* RecyclerView를 초기화하는 메서드 */
    fun setupRecyclerView(
        recyclerScanList: RecyclerView,
        mainActivityContext: Context
    ) {
        // RecyclerView의 레이아웃 매니저를 설정 (세로 방향 리스트 형태)
        recyclerScanList.layoutManager = LinearLayoutManager(mainActivityContext)
        // recyclerScanList 자체에서 데이터를 직접 관리 X
        // 그래서 데이터를 관리하고 화면에 표시하는 역할의 어댑터를 직접 넣어줘야함
        recyclerScanList.adapter = this
    }

    /* BLE 장치를 RecyclerView에 추가하는 메서드 */
    fun addDeviceToAdapt(deviceInfo: BluetoothDevice) {
        if (!devices.contains(deviceInfo)) { // 중복된 MAC 주소의 장치는 추가하지 않음
            devices.add(deviceInfo) // 새로운 BLE 장치 추가
            this.notifyItemInserted(devices.size - 1) // 새로 추가된 항목만 RecyclerView에 업데이트
        }
    }

    /* BLE 장치 목록을 초기화하는 메서드 */
    fun clearDevices() {
        devices.clear() // BLE 장치 리스트 초기화
        selectedPosition = -1 // 선택된 항목 초기화
        notifyDataSetChanged() // RecyclerView 갱신
    }
}