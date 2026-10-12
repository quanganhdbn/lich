package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SoundTone(val id: String, val displayName: String, val description: String) {
    CHIME_HIGH("chime_high", "Chuông Đón Khách (Chime VIP)", "Âm cao vang, rõ ràng khi có cuốc đón"),
    AIRPORT_CHIME("airport_chime", "Âm Báo Sân Bay (Airport)", "Âm giai điệu 2 nốt quen thuộc tại nhà ga"),
    LOUD_ALERT("loud_alert", "Còi Báo Lái Xe (Siren Khẩn)", "Âm báo dồn dập, chống lỡ giờ đón"),
    SOFT_BELL("soft_bell", "Chuông Nhẹ Nhàng (Soft Bell)", "Âm chuông êm dịu, không giật mình"),
    SYSTEM_DEFAULT("system_default", "Mặc Định Hệ Thống", "Âm thông báo chuẩn của điện thoại")
}

enum class UserRole(val id: String, val title: String, val subtitle: String) {
    DISPATCHER("dispatcher", "Người Điều Xe (Dispatcher)", "Toàn quyền quản lý, điều phối & giám sát đội xe"),
    DRIVER("driver", "Tài Xế (Driver)", "Tiếp nhận lịch chạy, đón trả khách & hoàn thành chuyến")
}

data class SetupState(
    // 1. Setup Chuông Thông Báo Đẩy
    val pushNotificationEnabled: Boolean = true,
    val selectedSound: SoundTone = SoundTone.CHIME_HIGH,
    val vibrationEnabled: Boolean = true,
    val defaultReminderMinutes: Int = 30, // 15, 30, 60, 120
    val autoFlightLandingReminder: Boolean = true,
    val soundVolumeLevel: Float = 0.85f,

    // 2. Setup Phân Quyền Người Điều Xe
    val currentRole: UserRole = UserRole.DISPATCHER,
    val dispatcherName: String = "Nguyễn Anh Tuấn (Tổ Điều Hành)",
    val dispatcherPhone: String = "0988 123 456",
    val fleetTeamName: String = "Đội Xe Đón Tiễn VIP Nội Bài",
    val canCreateTrips: Boolean = true,
    val canEditRouteTime: Boolean = true,
    val canCancelReassign: Boolean = true,
    val canApproveComplete: Boolean = true,
    val canViewFareRevenue: Boolean = true,
    val notifyOnTripFinished: Boolean = true,
    val isPinProtectionEnabled: Boolean = false,
    val pinCode: String = "1234"
)

class SetupManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ridepilot_setup_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    private fun loadInitialState(): SetupState {
        val soundId = prefs.getString("selected_sound", SoundTone.CHIME_HIGH.id) ?: SoundTone.CHIME_HIGH.id
        val soundTone = SoundTone.entries.find { it.id == soundId } ?: SoundTone.CHIME_HIGH

        val roleId = prefs.getString("current_role", UserRole.DISPATCHER.id) ?: UserRole.DISPATCHER.id
        val userRole = UserRole.entries.find { it.id == roleId } ?: UserRole.DISPATCHER

        return SetupState(
            pushNotificationEnabled = prefs.getBoolean("push_notif_enabled", true),
            selectedSound = soundTone,
            vibrationEnabled = prefs.getBoolean("vibration_enabled", true),
            defaultReminderMinutes = prefs.getInt("default_reminder_mins", 30),
            autoFlightLandingReminder = prefs.getBoolean("auto_flight_landing_reminder", true),
            soundVolumeLevel = prefs.getFloat("sound_volume_level", 0.85f),

            currentRole = userRole,
            dispatcherName = prefs.getString("dispatcher_name", "Nguyễn Anh Tuấn (Tổ Điều Hành)") ?: "Nguyễn Anh Tuấn (Tổ Điều Hành)",
            dispatcherPhone = prefs.getString("dispatcher_phone", "0988 123 456") ?: "0988 123 456",
            fleetTeamName = prefs.getString("fleet_team_name", "Đội Xe Đón Tiễn VIP Nội Bài") ?: "Đội Xe Đón Tiễn VIP Nội Bài",
            canCreateTrips = prefs.getBoolean("perm_create_trips", true),
            canEditRouteTime = prefs.getBoolean("perm_edit_route_time", true),
            canCancelReassign = prefs.getBoolean("perm_cancel_reassign", true),
            canApproveComplete = prefs.getBoolean("perm_approve_complete", true),
            canViewFareRevenue = prefs.getBoolean("perm_view_revenue", true),
            notifyOnTripFinished = prefs.getBoolean("perm_notify_finished", true),
            isPinProtectionEnabled = prefs.getBoolean("pin_protection_enabled", false),
            pinCode = prefs.getString("pin_code", "1234") ?: "1234"
        )
    }

    fun updatePushNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("push_notif_enabled", enabled).apply()
        _state.value = _state.value.copy(pushNotificationEnabled = enabled)
    }

    fun updateSelectedSound(sound: SoundTone) {
        prefs.edit().putString("selected_sound", sound.id).apply()
        _state.value = _state.value.copy(selectedSound = sound)
        previewSoundTone(sound)
    }

    fun updateVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
        _state.value = _state.value.copy(vibrationEnabled = enabled)
        if (enabled) {
            triggerVibrationSample()
        }
    }

    fun updateDefaultReminderMinutes(minutes: Int) {
        prefs.edit().putInt("default_reminder_mins", minutes).apply()
        _state.value = _state.value.copy(defaultReminderMinutes = minutes)
    }

    fun updateAutoFlightLandingReminder(enabled: Boolean) {
        prefs.edit().putBoolean("auto_flight_landing_reminder", enabled).apply()
        _state.value = _state.value.copy(autoFlightLandingReminder = enabled)
    }

    fun updateCurrentRole(role: UserRole) {
        prefs.edit().putString("current_role", role.id).apply()
        _state.value = _state.value.copy(currentRole = role)
    }

    fun updateDispatcherInfo(name: String, phone: String, fleet: String) {
        prefs.edit()
            .putString("dispatcher_name", name)
            .putString("dispatcher_phone", phone)
            .putString("fleet_team_name", fleet)
            .apply()
        _state.value = _state.value.copy(
            dispatcherName = name,
            dispatcherPhone = phone,
            fleetTeamName = fleet
        )
    }

    fun updatePermission(
        canCreate: Boolean? = null,
        canEdit: Boolean? = null,
        canCancel: Boolean? = null,
        canApprove: Boolean? = null,
        canViewRev: Boolean? = null,
        notifyFinished: Boolean? = null
    ) {
        val editor = prefs.edit()
        var current = _state.value

        canCreate?.let {
            editor.putBoolean("perm_create_trips", it)
            current = current.copy(canCreateTrips = it)
        }
        canEdit?.let {
            editor.putBoolean("perm_edit_route_time", it)
            current = current.copy(canEditRouteTime = it)
        }
        canCancel?.let {
            editor.putBoolean("perm_cancel_reassign", it)
            current = current.copy(canCancelReassign = it)
        }
        canApprove?.let {
            editor.putBoolean("perm_approve_complete", it)
            current = current.copy(canApproveComplete = it)
        }
        canViewRev?.let {
            editor.putBoolean("perm_view_revenue", it)
            current = current.copy(canViewFareRevenue = it)
        }
        notifyFinished?.let {
            editor.putBoolean("perm_notify_finished", it)
            current = current.copy(notifyOnTripFinished = it)
        }

        editor.apply()
        _state.value = current
    }

    fun updatePinProtection(enabled: Boolean, code: String = "1234") {
        prefs.edit()
            .putBoolean("pin_protection_enabled", enabled)
            .putString("pin_code", code)
            .apply()
        _state.value = _state.value.copy(
            isPinProtectionEnabled = enabled,
            pinCode = code
        )
    }

    fun previewSoundTone(sound: SoundTone = _state.value.selectedSound) {
        try {
            when (sound) {
                SoundTone.CHIME_HIGH -> {
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95)
                    tg.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 350)
                }
                SoundTone.AIRPORT_CHIME -> {
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                    tg.startTone(ToneGenerator.TONE_PROP_ACK, 400)
                }
                SoundTone.LOUD_ALERT -> {
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                    tg.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 500)
                }
                SoundTone.SOFT_BELL -> {
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                }
                SoundTone.SYSTEM_DEFAULT -> {
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val r = RingtoneManager.getRingtone(context, defaultUri)
                    r?.play()
                }
            }
        } catch (_: Exception) {
            // fallback
        }

        if (_state.value.vibrationEnabled) {
            triggerVibrationSample()
        }
    }

    private fun triggerVibrationSample() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(180)
                }
            }
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        private var instance: SetupManager? = null

        fun getInstance(context: Context): SetupManager {
            return instance ?: synchronized(this) {
                instance ?: SetupManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
