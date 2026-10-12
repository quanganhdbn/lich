package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import com.example.auth.AuthManager
import com.example.data.model.Trip
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TripViewModel
import com.example.ui.viewmodel.TripViewModelFactory

class MainActivity : ComponentActivity() {
    private val tripViewModel: TripViewModel by viewModels {
        TripViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AppRoot(tripViewModel = tripViewModel)
            }
        }
    }
}

@Composable
fun AppRoot(tripViewModel: TripViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val authUser by AuthManager.authState.collectAsState()

    var isCheckingAuth by remember { mutableStateOf(authUser == null) }

    LaunchedEffect(Unit) {
        if (AuthManager.currentUser == null) {
            AuthManager.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isCheckingAuth = false
                },
                onUnauthenticated = {
                    isCheckingAuth = false
                },
                scope = coroutineScope
            )
        } else {
            isCheckingAuth = false
        }
    }

    LaunchedEffect(authUser?.uid) {
        authUser?.uid?.let { uid ->
            tripViewModel.onUserAuthenticated(uid)
        }
    }

    if (isCheckingAuth) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (authUser == null) {
        SignInScreen(
            onAuthSuccess = {
                AuthManager.currentUser?.uid?.let { uid ->
                    tripViewModel.onUserAuthenticated(uid)
                }
            }
        )
    } else {
        MainAppScreen(
            viewModel = tripViewModel,
            onSignOut = {
                AuthManager.signOut(
                    credentialManager = credentialManager,
                    onSignOutComplete = {},
                    scope = coroutineScope
                )
            }
        )
    }
}

@Composable
fun MainAppScreen(
    viewModel: TripViewModel,
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentTab by remember { mutableIntStateOf(0) }

    // Runtime Notification Permission launcher for Android 13+ (TIRAMISU)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.sendTestNotificationNow()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Dialog state collections from ViewModel
    val showTripDialog by viewModel.showTripDialog.collectAsState()
    val tripForEdit by viewModel.activeTripForEdit.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    val showAiDialog by viewModel.showAiDialog.collectAsState()
    val aiParseState by viewModel.aiParseState.collectAsState()

    val selectedTripDetails by viewModel.selectedTripDetails.collectAsState()
    val quickTripAction by viewModel.quickTripAction.collectAsState()
    val quickActionType by viewModel.quickActionType.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = {
                        Icon(
                            if (currentTab == 0) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                            contentDescription = "Lịch Chạy"
                        )
                    },
                    label = { Text("Lịch Chạy") },
                    modifier = Modifier.testTag("nav_tab_schedule")
                )

                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = {
                        Icon(
                            if (currentTab == 1) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Cài đặt"
                        )
                    },
                    label = { Text("Cài đặt") },
                    modifier = Modifier.testTag("nav_tab_setup")
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                0 -> DailyScheduleScreen(viewModel = viewModel)
                1 -> SetupScreen(
                    viewModel = viewModel,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.sendTestNotificationNow()
                        }
                    },
                    onSignOut = onSignOut
                )
            }
        }
    }

    // Add / Edit Trip Dialog
    // Add / Edit Trip Dialog with Integrated AI Quick Fill
    if (showTripDialog) {
        AddEditTripDialog(
            tripToEdit = tripForEdit,
            defaultDate = selectedDate,
            initialAiOpen = false,
            onDismiss = { viewModel.closeTripDialog() },
            onSave = { trip -> viewModel.saveTrip(trip) }
        )
    }

    // When AI button is clicked, open Add Trip with AI quick fill expanded
    if (showAiDialog) {
        AddEditTripDialog(
            tripToEdit = null,
            defaultDate = selectedDate,
            initialAiOpen = true,
            onDismiss = { viewModel.closeAiDialog() },
            onSave = { trip -> viewModel.saveTrip(trip) }
        )
    }

    // Quick Start Dialog
    if (quickActionType == "START" && quickTripAction != null) {
        QuickStartTripDialog(
            trip = quickTripAction!!,
            onDismiss = { viewModel.closeQuickAction() },
            onConfirm = { viewModel.startTrip(quickTripAction!!.id) }
        )
    }

    // Quick Finish Dialog
    if (quickActionType == "FINISH" && quickTripAction != null) {
        QuickFinishTripDialog(
            trip = quickTripAction!!,
            onDismiss = { viewModel.closeQuickAction() },
            onConfirm = { viewModel.finishTrip(quickTripAction!!.id) }
        )
    }

    // Set Reminder Dialog
    if (quickActionType == "REMINDER" && quickTripAction != null) {
        SetReminderDialog(
            trip = quickTripAction!!,
            onDismiss = { viewModel.closeQuickAction() },
            onSetReminder = { timestamp -> viewModel.setTripReminder(quickTripAction!!, timestamp) },
            onCancelReminder = { viewModel.cancelTripReminder(quickTripAction!!) }
        )
    }

    // Trip Details BottomSheet / Dialog
    selectedTripDetails?.let { trip ->
        TripDetailsBottomSheet(
            trip = trip,
            onDismiss = { viewModel.closeTripDetails() },
            onEdit = {
                viewModel.closeTripDetails()
                viewModel.openEditTripDialog(trip)
            },
            onDelete = { viewModel.deleteTrip(trip) },
            onStartTrip = {
                viewModel.closeTripDetails()
                viewModel.openQuickAction(trip, "START")
            },
            onFinishTrip = {
                viewModel.closeTripDetails()
                viewModel.finishTrip(trip.id)
            },
            onReopenTrip = {
                viewModel.closeTripDetails()
                viewModel.reopenTrip(trip.id)
            },
            onCancelTrip = { viewModel.cancelTrip(trip.id) },
            onReminderClick = {
                viewModel.closeTripDetails()
                viewModel.openQuickAction(trip, "REMINDER")
            }
        )
    }
}
