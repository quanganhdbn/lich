package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.LichXeApplication
import com.example.data.SetupManager
import com.example.data.SetupState
import com.example.data.SoundTone
import com.example.data.UserRole
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.network.AirlabsService
import com.example.network.FlightInfo
import com.example.network.GeminiService
import com.example.network.ParsedTripData
import com.example.notification.TripReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface AiParseUiState {
    object Idle : AiParseUiState
    object Loading : AiParseUiState
    data class Success(val data: ParsedTripData) : AiParseUiState
    data class Error(val message: String) : AiParseUiState
}

sealed interface FlightLookupUiState {
    object Idle : FlightLookupUiState
    object Loading : FlightLookupUiState
    data class Success(val flight: FlightInfo) : FlightLookupUiState
    data class Error(val message: String) : FlightLookupUiState
}

class TripViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LichXeApplication).repository
    val setupManager = SetupManager.getInstance(application)
    val setupState: StateFlow<SetupState> = setupManager.state

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayString: String = dateFormat.format(Date())

    private val _selectedDate = MutableStateFlow(todayString)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // 2 TAB DUY NHẤT: "SCHEDULE" (Lịch Chạy) & "COMPLETED" (Đã Hoàn Thành)
    // Mặc định là "SCHEDULE" để khi tích hoàn thành chuyến tự mất và sang mục đã hoàn thành
    private val _selectedStatusFilter = MutableStateFlow("SCHEDULE")
    val selectedStatusFilter: StateFlow<String> = _selectedStatusFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allTrips: StateFlow<List<Trip>> = repository.allTrips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentDayScheduleCount: StateFlow<Int> = combine(allTrips, _selectedDate) { trips, date ->
        trips.count { it.tripDate == date && it.status != TripStatus.COMPLETED.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentDayCompletedCount: StateFlow<Int> = combine(allTrips, _selectedDate) { trips, date ->
        trips.count { it.tripDate == date && it.status == TripStatus.COMPLETED.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val filteredTrips: StateFlow<List<Trip>> = combine(
        allTrips,
        _selectedDate,
        _selectedStatusFilter,
        _searchQuery
    ) { trips, date, filter, query ->
        trips.filter { trip ->
            val matchDate = trip.tripDate == date
            val matchFilter = when (filter) {
                "COMPLETED" -> trip.status == TripStatus.COMPLETED.name
                "SCHEDULE" -> trip.status != TripStatus.COMPLETED.name
                else -> trip.status != TripStatus.COMPLETED.name
            }
            val matchQuery = if (query.isBlank()) true else {
                trip.startLocation.contains(query, ignoreCase = true) ||
                trip.destination.contains(query, ignoreCase = true) ||
                trip.customerName.contains(query, ignoreCase = true) ||
                trip.customerPhone.contains(query, ignoreCase = true) ||
                trip.flightNumber.contains(query, ignoreCase = true)
            }
            matchDate && matchFilter && matchQuery
        }.sortedWith(compareBy<Trip> { it.startTime }.thenBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onUserAuthenticated(userId: String) {
        repository.startListeningToCloudTrips(userId)
    }

    // AI Parsing State
    private val _aiParseState = MutableStateFlow<AiParseUiState>(AiParseUiState.Idle)
    val aiParseState: StateFlow<AiParseUiState> = _aiParseState.asStateFlow()

    // Dialogs
    private val _activeTripForEdit = MutableStateFlow<Trip?>(null)
    val activeTripForEdit: StateFlow<Trip?> = _activeTripForEdit.asStateFlow()

    private val _showTripDialog = MutableStateFlow(false)
    val showTripDialog: StateFlow<Boolean> = _showTripDialog.asStateFlow()

    private val _showAiDialog = MutableStateFlow(false)
    val showAiDialog: StateFlow<Boolean> = _showAiDialog.asStateFlow()

    private val _selectedTripDetails = MutableStateFlow<Trip?>(null)
    val selectedTripDetails: StateFlow<Trip?> = _selectedTripDetails.asStateFlow()

    private val _quickTripAction = MutableStateFlow<Trip?>(null)
    val quickTripAction: StateFlow<Trip?> = _quickTripAction.asStateFlow()

    private val _quickActionType = MutableStateFlow("") // "START" or "FINISH" or "REMINDER"
    val quickActionType: StateFlow<String> = _quickActionType.asStateFlow()

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun goToToday() {
        _selectedDate.value = todayString
    }

    fun goToPreviousDay() {
        try {
            val dateObj = dateFormat.parse(_selectedDate.value) ?: return
            val cal = Calendar.getInstance().apply {
                time = dateObj
                add(Calendar.DAY_OF_YEAR, -1)
            }
            _selectedDate.value = dateFormat.format(cal.time)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun goToNextDay() {
        try {
            val dateObj = dateFormat.parse(_selectedDate.value) ?: return
            val cal = Calendar.getInstance().apply {
                time = dateObj
                add(Calendar.DAY_OF_YEAR, 1)
            }
            _selectedDate.value = dateFormat.format(cal.time)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun setStatusFilter(filter: String) {
        _selectedStatusFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openNewTripDialog() {
        _activeTripForEdit.value = null
        _showTripDialog.value = true
    }

    fun openEditTripDialog(trip: Trip) {
        _activeTripForEdit.value = trip
        _showTripDialog.value = true
    }

    fun closeTripDialog() {
        _showTripDialog.value = false
        _activeTripForEdit.value = null
    }

    fun openAiDialog() {
        _aiParseState.value = AiParseUiState.Idle
        _showAiDialog.value = true
    }

    fun closeAiDialog() {
        _showAiDialog.value = false
    }

    fun openTripDetails(trip: Trip) {
        _selectedTripDetails.value = trip
    }

    fun closeTripDetails() {
        _selectedTripDetails.value = null
    }

    fun openQuickAction(trip: Trip, actionType: String) {
        _quickTripAction.value = trip
        _quickActionType.value = actionType
    }

    fun closeQuickAction() {
        _quickTripAction.value = null
        _quickActionType.value = ""
    }

    fun saveTrip(trip: Trip) {
        viewModelScope.launch {
            if (trip.id == 0L) {
                repository.insertTrip(trip)
                TripReminderScheduler.showInstantNotification(
                    getApplication(),
                    "Đã thêm chuyến đón mới",
                    "${trip.startTime}: ${trip.startLocation} ➔ ${trip.destination}"
                )
            } else {
                repository.updateTrip(trip)
            }
            closeTripDialog()
        }
    }

    fun deleteTrip(trip: Trip) {
        viewModelScope.launch {
            repository.deleteTrip(trip)
            closeTripDetails()
        }
    }

    fun startTrip(tripId: Long) {
        viewModelScope.launch {
            val trip = allTrips.value.firstOrNull { it.id == tripId } ?: return@launch
            val updated = trip.copy(status = TripStatus.IN_PROGRESS.name)
            repository.updateTrip(updated)
            TripReminderScheduler.showInstantNotification(
                getApplication(),
                "Chuyến đi đang bắt đầu",
                "Đang di chuyển đến ${updated.destination}"
            )
            closeQuickAction()
            if (_selectedTripDetails.value?.id == tripId) {
                _selectedTripDetails.value = updated
            }
        }
    }

    fun finishTrip(tripId: Long) {
        viewModelScope.launch {
            val trip = allTrips.value.firstOrNull { it.id == tripId } ?: return@launch
            val nowTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val updated = trip.copy(
                status = TripStatus.COMPLETED.name,
                endTime = nowTime
            )
            repository.updateTrip(updated)
            TripReminderScheduler.showInstantNotification(
                getApplication(),
                "Hoàn thành chuyến đi!",
                "Đã hoàn thành chuyến đi đến ${updated.destination}"
            )
            closeQuickAction()
            if (_selectedTripDetails.value?.id == tripId) {
                _selectedTripDetails.value = updated
            }
        }
    }

    fun reopenTrip(tripId: Long) {
        viewModelScope.launch {
            val trip = allTrips.value.firstOrNull { it.id == tripId } ?: return@launch
            val updated = trip.copy(
                status = TripStatus.SCHEDULED.name,
                endTime = ""
            )
            repository.updateTrip(updated)
            if (_selectedTripDetails.value?.id == tripId) {
                _selectedTripDetails.value = updated
            }
        }
    }

    fun cancelTrip(tripId: Long) {
        viewModelScope.launch {
            val trip = allTrips.value.firstOrNull { it.id == tripId } ?: return@launch
            val updated = trip.copy(status = TripStatus.CANCELLED.name)
            repository.updateTrip(updated)
            closeQuickAction()
            if (_selectedTripDetails.value?.id == tripId) {
                _selectedTripDetails.value = updated
            }
        }
    }

    fun setTripReminder(trip: Trip, reminderMillis: Long) {
        viewModelScope.launch {
            val updated = trip.copy(
                reminderEnabled = true,
                reminderTimeMillis = reminderMillis
            )
            repository.updateTrip(updated)
            val format = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
            TripReminderScheduler.showInstantNotification(
                getApplication(),
                "Đã hẹn giờ nhắc nhở",
                "Hệ thống sẽ nhắc nhở lúc ${format.format(Date(reminderMillis))} (${trip.startLocation} ➔ ${trip.destination})"
            )
            closeQuickAction()
            if (_selectedTripDetails.value?.id == trip.id) {
                _selectedTripDetails.value = updated
            }
        }
    }

    fun cancelTripReminder(trip: Trip) {
        viewModelScope.launch {
            val updated = trip.copy(reminderEnabled = false, reminderTimeMillis = 0L)
            repository.updateTrip(updated)
            closeQuickAction()
            if (_selectedTripDetails.value?.id == trip.id) {
                _selectedTripDetails.value = updated
            }
        }
    }

    fun parseScheduleWithAi(prompt: String) {
        viewModelScope.launch {
            _aiParseState.value = AiParseUiState.Loading
            val result = GeminiService.parseScheduleFromPrompt(prompt)
            result.fold(
                onSuccess = { data ->
                    _aiParseState.value = AiParseUiState.Success(data)
                },
                onFailure = { err ->
                    _aiParseState.value = AiParseUiState.Error(err.message ?: "Không thể phân tích dữ liệu")
                }
            )
        }
    }

    fun sendTestNotificationNow() {
        TripReminderScheduler.showInstantNotification(
            getApplication(),
            "🔔 Kiểm tra thông báo đẩy RidePilot",
            "Thông báo đẩy hoạt động chuẩn xác! Hệ thống nhắc nhở lịch đón tiễn đã sẵn sàng."
        )
    }
}

class TripViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TripViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TripViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
