package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.local.MemoryTypes
import com.example.ui.components.GlassBackground
import com.example.ui.screens.AdminConfigPanelScreen
import com.example.ui.screens.AppLockGateScreen
import com.example.ui.screens.AskAiScreen
import com.example.ui.screens.AuthChoiceScreen
import com.example.ui.screens.BackupAndCloudScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LifeTrackersScreen
import com.example.ui.screens.MemoriesVaultScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PrivateVaultScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SubscriptionAndPaymentsScreen
import com.example.core.datastore.ThemeMode
import com.example.ui.screens.TrackerSegment
import com.example.ui.theme.YaadRakshakTheme
import com.example.ui.viewmodel.AiAndBillingViewModel
import com.example.ui.viewmodel.AppEntryState
import com.example.ui.viewmodel.SessionViewModel
import com.example.ui.viewmodel.VaultViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data class MemoriesRoute(val timeline: Boolean = false)
@Serializable data object AskAiRoute
@Serializable data class TrackersRoute(val segment: String = "REMINDERS")
@Serializable data object ProfileRoute
@Serializable data object PrivateVaultRoute
@Serializable data object SubscriptionRoute
@Serializable data object BackupRoute
@Serializable data object AdminRoute

private data class BottomNavTab(
    val label: String,
    val icon: ImageVector,
    val tag: String,
    val route: Any
)

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            val sessionViewModel: SessionViewModel = hiltViewModel()
            val preferences by sessionViewModel.preferences.collectAsStateWithLifecycle()
            val entryState by sessionViewModel.entryState.collectAsStateWithLifecycle()
            val isSigningIn by sessionViewModel.isSigningIn.collectAsStateWithLifecycle()
            val authError by sessionViewModel.authError.collectAsStateWithLifecycle()

            val useDark = when (preferences.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(useDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (useDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            YaadRakshakTheme(themeMode = preferences.themeMode) {
                val context = LocalContext.current
                when (val state = entryState) {
                    AppEntryState.Splash -> SplashScreen()
                    AppEntryState.Onboarding -> OnboardingScreen(
                        onFinish = { sessionViewModel.completeOnboarding() },
                        onContinueGuest = { sessionViewModel.continueAsGuest() }
                    )
                    AppEntryState.AuthChoice -> AuthChoiceScreen(
                        isLoading = isSigningIn,
                        errorMessage = authError,
                        onGoogleSignIn = { sessionViewModel.signInWithGoogle(context) },
                        onContinueGuest = { sessionViewModel.continueAsGuest() }
                    )
                    is AppEntryState.LockGate -> AppLockGateScreen(
                        biometricEnabled = state.biometricEnabled,
                        onVerifyPin = { pin -> sessionViewModel.verifyAppPin(pin) },
                        onBiometricSuccess = { sessionViewModel.unlockWithBiometric() }
                    )
                    is AppEntryState.AuthenticatedReady -> {
                        val vaultViewModel: VaultViewModel = hiltViewModel()
                        val aiAndBillingViewModel: AiAndBillingViewModel = hiltViewModel()
                        AuthenticatedMainShell(
                            session = state,
                            sessionViewModel = sessionViewModel,
                            vaultViewModel = vaultViewModel,
                            aiAndBillingViewModel = aiAndBillingViewModel
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedMainShell(
    session: AppEntryState.AuthenticatedReady,
    sessionViewModel: SessionViewModel,
    vaultViewModel: VaultViewModel,
    aiAndBillingViewModel: AiAndBillingViewModel
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(session.userId) {
        vaultViewModel.bindUser(session.userId)
        aiAndBillingViewModel.bindUser(session.userId)
    }

    val preferences by sessionViewModel.preferences.collectAsStateWithLifecycle()
    val user by vaultViewModel.currentUser.collectAsStateWithLifecycle()
    val memories by vaultViewModel.memories.collectAsStateWithLifecycle()
    val folders by vaultViewModel.folders.collectAsStateWithLifecycle()
    val reminders by vaultViewModel.reminders.collectAsStateWithLifecycle()
    val warranties by vaultViewModel.warranties.collectAsStateWithLifecycle()
    val receipts by vaultViewModel.receipts.collectAsStateWithLifecycle()
    val expenses by vaultViewModel.expenses.collectAsStateWithLifecycle()
    val contacts by vaultViewModel.contacts.collectAsStateWithLifecycle()
    val searchQuery by vaultViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedType by vaultViewModel.selectedTypeFilter.collectAsStateWithLifecycle()
    val ocrBusy by vaultViewModel.ocrBusy.collectAsStateWithLifecycle()
    val privateUnlocked by vaultViewModel.privateVaultUnlocked.collectAsStateWithLifecycle()
    val statusBanner by vaultViewModel.statusBanner.collectAsStateWithLifecycle()

    val conversations by aiAndBillingViewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by aiAndBillingViewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by aiAndBillingViewModel.messages.collectAsStateWithLifecycle()
    val sources by aiAndBillingViewModel.sources.collectAsStateWithLifecycle()
    val suggestions by aiAndBillingViewModel.suggestions.collectAsStateWithLifecycle()
    val aiLoading by aiAndBillingViewModel.aiLoading.collectAsStateWithLifecycle()
    val aiNotice by aiAndBillingViewModel.aiNotice.collectAsStateWithLifecycle()
    val subscription by aiAndBillingViewModel.subscription.collectAsStateWithLifecycle()
    val entitlement by aiAndBillingViewModel.entitlement.collectAsStateWithLifecycle()
    val plans by aiAndBillingViewModel.availablePlans.collectAsStateWithLifecycle()
    val orders by aiAndBillingViewModel.paymentOrders.collectAsStateWithLifecycle()
    val transactions by aiAndBillingViewModel.paymentTransactions.collectAsStateWithLifecycle()
    val merchantInfo by aiAndBillingViewModel.merchantInfo.collectAsStateWithLifecycle()
    val latestCreatedOrder by aiAndBillingViewModel.latestCreatedOrder.collectAsStateWithLifecycle()
    val billingMessage by aiAndBillingViewModel.billingMessage.collectAsStateWithLifecycle()
    val checkoutUrlToOpen by aiAndBillingViewModel.checkoutUrlToOpen.collectAsStateWithLifecycle()
    val upiPayUriToOpen by aiAndBillingViewModel.upiPayUriToOpen.collectAsStateWithLifecycle()
    val backupJobs by aiAndBillingViewModel.backupJobs.collectAsStateWithLifecycle()
    val backupMessage by aiAndBillingViewModel.backupMessage.collectAsStateWithLifecycle()
    val adminOverview by aiAndBillingViewModel.adminOverview.collectAsStateWithLifecycle()
    val adminModels by aiAndBillingViewModel.adminModels.collectAsStateWithLifecycle()
    val adminMessage by aiAndBillingViewModel.adminMessage.collectAsStateWithLifecycle()

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            vaultViewModel.clearBanner()
        }
    }

    LaunchedEffect(checkoutUrlToOpen) {
        checkoutUrlToOpen?.let { url ->
            runCatching {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            }.onFailure {
                snackbarHostState.showSnackbar("Unable to open checkout URL in browser.")
            }
            aiAndBillingViewModel.consumeCheckoutUrl()
        }
    }

    LaunchedEffect(upiPayUriToOpen) {
        upiPayUriToOpen?.let { uriStr ->
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
                context.startActivity(Intent.createChooser(intent, "Pay with UPI App"))
            }.onFailure {
                snackbarHostState.showSnackbar("No UPI app detected on this device. Pay using the Admin UPI/Bank details and submit your UTR.")
            }
            aiAndBillingViewModel.consumeUpiUri()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val isTopLevel = currentDest == null ||
        currentDest.hasRoute<HomeRoute>() ||
        currentDest.hasRoute<MemoriesRoute>() ||
        currentDest.hasRoute<AskAiRoute>() ||
        currentDest.hasRoute<TrackersRoute>() ||
        currentDest.hasRoute<ProfileRoute>()

    val topTitle = when {
        currentDest?.hasRoute<MemoriesRoute>() == true -> "Memory Vault"
        currentDest?.hasRoute<AskAiRoute>() == true -> "Ask YaadRakshak AI"
        currentDest?.hasRoute<TrackersRoute>() == true -> "Life Trackers"
        currentDest?.hasRoute<ProfileRoute>() == true -> "Profile & Settings"
        currentDest?.hasRoute<PrivateVaultRoute>() == true -> "Private Vault"
        currentDest?.hasRoute<SubscriptionRoute>() == true -> "Premium & YouPI PG"
        currentDest?.hasRoute<BackupRoute>() == true -> "Encrypted Local Backup"
        currentDest?.hasRoute<AdminRoute>() == true -> "Admin Control Center"
        else -> "YaadRakshak"
    }

    val bottomTabs = remember {
        listOf(
            BottomNavTab("Home", Icons.Default.Home, "nav_home", HomeRoute),
            BottomNavTab("Memories", Icons.Default.FolderSpecial, "nav_memories", MemoriesRoute()),
            BottomNavTab("Ask AI", Icons.Default.AutoAwesome, "nav_ask_ai", AskAiRoute),
            BottomNavTab("Trackers", Icons.Default.NotificationsActive, "nav_trackers", TrackersRoute()),
            BottomNavTab("Profile", Icons.Default.Person, "nav_profile", ProfileRoute)
        )
    }

    fun navigateTopTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateHub(route: Any) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("YaadRakshak", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Your Entire Life, Searchable in One Place.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                val drawerColors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary
                )
                NavigationDrawerItem(
                    label = { Text("Memory Timeline") },
                    selected = false,
                    icon = { Icon(Icons.Default.Timeline, contentDescription = null) },
                    colors = drawerColors,
                    onClick = { navigateHub(MemoriesRoute(timeline = true)) }
                )
                NavigationDrawerItem(
                    label = { Text("Private Vault (AES-256)") },
                    selected = currentDest?.hasRoute<PrivateVaultRoute>() == true,
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    colors = drawerColors,
                    onClick = { navigateHub(PrivateVaultRoute) }
                )
                NavigationDrawerItem(
                    label = { Text("Warranty Manager") },
                    selected = false,
                    icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                    colors = drawerColors,
                    onClick = { navigateHub(TrackersRoute(TrackerSegment.WARRANTIES.name)) }
                )
                NavigationDrawerItem(
                    label = { Text("Receipts & Expenses") },
                    selected = false,
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                    colors = drawerColors,
                    onClick = { navigateHub(TrackersRoute(TrackerSegment.EXPENSES.name)) }
                )
                NavigationDrawerItem(
                    label = { Text("Personal Contacts") },
                    selected = false,
                    icon = { Icon(Icons.Default.Contacts, contentDescription = null) },
                    colors = drawerColors,
                    onClick = { navigateHub(TrackersRoute(TrackerSegment.CONTACTS.name)) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline
                )
                NavigationDrawerItem(
                    label = { Text("Premium & YouPI PG") },
                    selected = currentDest?.hasRoute<SubscriptionRoute>() == true,
                    icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) },
                    colors = drawerColors,
                    onClick = { navigateHub(SubscriptionRoute) }
                )
                NavigationDrawerItem(
                    label = { Text("Encrypted Local Backup (.yrbak)") },
                    selected = currentDest?.hasRoute<BackupRoute>() == true,
                    icon = { Icon(Icons.Default.Backup, contentDescription = null) },
                    colors = drawerColors,
                    onClick = { navigateHub(BackupRoute) }
                )
                if (session.isAdmin) {
                    NavigationDrawerItem(
                        label = { Text("Admin Configuration Panel") },
                        selected = currentDest?.hasRoute<AdminRoute>() == true,
                        icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                        colors = drawerColors,
                        onClick = { navigateHub(AdminRoute) }
                    )
                }
            }
        }
    ) {
        GlassBackground {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Spacer(Modifier.height(16.dp))
                            bottomTabs.forEach { tab ->
                                val selected = when (tab.route) {
                                    is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                    is MemoriesRoute -> currentDest?.hasRoute<MemoriesRoute>() == true
                                    is AskAiRoute -> currentDest?.hasRoute<AskAiRoute>() == true
                                    is TrackersRoute -> currentDest?.hasRoute<TrackersRoute>() == true
                                    is ProfileRoute -> currentDest?.hasRoute<ProfileRoute>() == true
                                    else -> false
                                }
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }

                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                                ),
                                navigationIcon = {
                                    if (!isTopLevel) {
                                        IconButton(onClick = { navController.popBackStack() }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { scope.launch { drawerState.open() } },
                                            modifier = Modifier.testTag("open_drawer_button")
                                        ) {
                                            Icon(Icons.Default.Menu, contentDescription = "Open Menu")
                                        }
                                    }
                                },
                                title = { Text(topTitle, style = MaterialTheme.typography.titleLarge) },
                                actions = {
                                    IconButton(onClick = { navController.navigate(PrivateVaultRoute) }) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Private Vault",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            if (!useRail && isTopLevel) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 3.dp,
                                    modifier = Modifier.navigationBarsPadding()
                                ) {
                                    bottomTabs.forEach { tab ->
                                        val selected = when (tab.route) {
                                            is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                            is MemoriesRoute -> currentDest?.hasRoute<MemoriesRoute>() == true
                                            is AskAiRoute -> currentDest?.hasRoute<AskAiRoute>() == true
                                            is TrackersRoute -> currentDest?.hasRoute<TrackersRoute>() == true
                                            is ProfileRoute -> currentDest?.hasRoute<ProfileRoute>() == true
                                            else -> false
                                        }
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTopTab(tab.route) },
                                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                                            label = { Text(tab.label, maxLines = 1) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                            ),
                                            modifier = Modifier.testTag(tab.tag)
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = HomeRoute
                            ) {
                                composable<HomeRoute> {
                                    HomeDashboardScreen(
                                        displayName = session.displayName,
                                        isGuest = session.isGuest,
                                        user = user,
                                        subscription = subscription,
                                        entitlement = entitlement,
                                        memories = memories,
                                        reminders = reminders,
                                        warranties = warranties,
                                        expenses = expenses,
                                        onQuickScanDoc = { navigateTopTab(MemoriesRoute()) },
                                        onQuickImportFile = { navigateTopTab(MemoriesRoute()) },
                                        onQuickVoiceNote = { navigateTopTab(MemoriesRoute()) },
                                        onQuickAskAi = { navigateTopTab(AskAiRoute) },
                                        onQuickAddExpense = {
                                            navController.navigate(TrackersRoute(TrackerSegment.EXPENSES.name))
                                        },
                                        onOpenPrivateVault = { navController.navigate(PrivateVaultRoute) },
                                        onOpenMemories = { navigateTopTab(MemoriesRoute()) },
                                        onOpenReminders = { navigateTopTab(TrackersRoute(TrackerSegment.REMINDERS.name)) },
                                        onOpenUpgrade = { navController.navigate(SubscriptionRoute) },
                                        onMemoryClick = { navigateTopTab(MemoriesRoute()) }
                                    )
                                }

                                composable<MemoriesRoute> { backStackEntry ->
                                    val route = backStackEntry.toRoute<MemoriesRoute>()
                                    MemoriesVaultScreen(
                                        memories = memories,
                                        folders = folders,
                                        searchQuery = searchQuery,
                                        selectedType = selectedType,
                                        ocrBusy = ocrBusy,
                                        isGuest = session.isGuest,
                                        entitlement = entitlement,
                                        initialTimelineMode = route.timeline,
                                        onSearchChange = { vaultViewModel.setSearchQuery(it) },
                                        onTypeChange = { vaultViewModel.setTypeFilter(it) },
                                        onCreateFolder = { vaultViewModel.createFolder(it) },
                                        onDeleteFolder = { vaultViewModel.deleteFolder(it) },
                                        onOpenUpgrade = { navController.navigate(SubscriptionRoute) },
                                        onSaveManualMemory = { title, content, type, cat, tags, fId, priv, enrich ->
                                            vaultViewModel.saveManualMemory(
                                                title = title,
                                                content = content,
                                                memoryType = type,
                                                category = cat,
                                                tagsCsv = tags,
                                                folderId = fId,
                                                isPrivate = priv,
                                                autoEnrichWithAi = enrich,
                                                isGuest = session.isGuest
                                            )
                                        },
                                        onUpdateManualMemory = { id, title, content, type, cat, tags, fId, priv, enrich ->
                                            vaultViewModel.saveManualMemory(
                                                title = title,
                                                content = content,
                                                memoryType = type,
                                                category = cat,
                                                tagsCsv = tags,
                                                folderId = fId,
                                                isPrivate = priv,
                                                autoEnrichWithAi = enrich,
                                                isGuest = session.isGuest,
                                                existingId = id
                                            )
                                        },
                                        onImportUri = { uri, title, type, priv ->
                                            vaultViewModel.importFileOrImage(uri, title, type, priv, session.isGuest)
                                        },
                                        onScannedImageFile = { file, title, type ->
                                            vaultViewModel.processScannedImageFile(file, title, type)
                                        },
                                        onSaveVoiceMemory = { title, transcript, audioPath, durationMs ->
                                            vaultViewModel.saveVoiceMemory(title, transcript, audioPath, durationMs, session.isGuest)
                                        },
                                        onDeleteMemory = { vaultViewModel.deleteMemory(it) }
                                    )
                                }

                                composable<AskAiRoute> {
                                    AskAiScreen(
                                        conversations = conversations,
                                        activeConversationId = activeConversationId,
                                        messages = messages,
                                        sources = sources,
                                        suggestions = suggestions,
                                        isLoading = aiLoading,
                                        noticeMessage = aiNotice,
                                        memories = memories,
                                        aiCreditsRemaining = user?.aiCreditsRemaining ?: entitlement.aiCreditsRemaining,
                                        aiMonthlyCredits = entitlement.aiMonthlyCredits,
                                        onSelectConversation = { aiAndBillingViewModel.selectConversation(it) },
                                        onDeleteConversation = { aiAndBillingViewModel.deleteConversation(it) },
                                        onNewConversation = { aiAndBillingViewModel.startNewConversation() },
                                        onAskQuestion = { q -> aiAndBillingViewModel.askAi(q, session.isGuest, session.isAdmin) },
                                        onOpenMemory = { navigateTopTab(MemoriesRoute()) }
                                    )
                                }

                                composable<TrackersRoute> { backStackEntry ->
                                    val route = backStackEntry.toRoute<TrackersRoute>()
                                    val seg = runCatching { TrackerSegment.valueOf(route.segment) }
                                        .getOrDefault(TrackerSegment.REMINDERS)
                                    LifeTrackersScreen(
                                        reminders = reminders,
                                        warranties = warranties,
                                        receipts = receipts,
                                        expenses = expenses,
                                        contacts = contacts,
                                        initialSegment = seg,
                                        onAddReminder = { t, n, due, prio, rep ->
                                            vaultViewModel.addReminder(t, n, due, prio, rep)
                                        },
                                        onUpdateReminder = { vaultViewModel.updateReminder(it) },
                                        onDeleteReminder = { vaultViewModel.deleteReminder(it) },
                                        onToggleReminder = { vaultViewModel.toggleReminder(it) },
                                        onAddWarranty = { p, b, s, pM, eM, cov, ph ->
                                            vaultViewModel.addWarranty(p, b, s, pM, eM, cov, ph)
                                        },
                                        onUpdateWarranty = { vaultViewModel.updateWarranty(it) },
                                        onDeleteWarranty = { vaultViewModel.deleteWarranty(it) },
                                        onAddReceipt = { m, tot, tax, inv, cat ->
                                            vaultViewModel.addReceipt(m, tot, tax, inv, cat)
                                        },
                                        onUpdateReceipt = { vaultViewModel.updateReceipt(it) },
                                        onDeleteReceipt = { vaultViewModel.deleteReceipt(it) },
                                        onAddExpense = { t, c, amt, notes ->
                                            vaultViewModel.addExpense(t, c, amt, notes)
                                        },
                                        onUpdateExpense = { vaultViewModel.updateExpense(it) },
                                        onDeleteExpense = { vaultViewModel.deleteExpense(it) },
                                        onAddContact = { n, rel, ph, em, notes ->
                                            vaultViewModel.addContact(n, rel, ph, em, notes)
                                        },
                                        onUpdateContact = { vaultViewModel.updateContact(it) },
                                        onDeleteContact = { vaultViewModel.deleteContact(it) }
                                    )
                                }

                                composable<ProfileRoute> {
                                    ProfileSettingsScreen(
                                        displayName = session.displayName,
                                        email = session.email,
                                        isGuest = session.isGuest,
                                        isAdmin = session.isAdmin,
                                        user = user,
                                        preferences = preferences,
                                        subscription = subscription,
                                        entitlement = entitlement,
                                        memoryCount = memories.size,
                                        reminderCount = reminders.size,
                                        warrantyCount = warranties.size,
                                        receiptCount = receipts.size,
                                        contactCount = contacts.size,
                                        onSetTheme = { sessionViewModel.setThemeMode(it) },
                                        onConfigureLock = { pin, enable, bio ->
                                            sessionViewModel.configureAppLock(pin, enable, bio)
                                        },
                                        onSetBackendUrl = { sessionViewModel.setBackendBaseUrl(it) },
                                        onSetCloudSync = { sessionViewModel.setCloudSyncEnabled(it) },
                                        onTriggerSync = { vaultViewModel.triggerCloudSync() },
                                        onOpenSubscription = { navController.navigate(SubscriptionRoute) },
                                        onOpenBackup = { navController.navigate(BackupRoute) },
                                        onOpenAdmin = { navController.navigate(AdminRoute) },
                                        onGoogleSignIn = { sessionViewModel.signInWithGoogle(context) },
                                        onSignOut = { sessionViewModel.signOut(context) }
                                    )
                                }

                                composable<PrivateVaultRoute> {
                                    PrivateVaultScreen(
                                        memories = memories,
                                        isUnlocked = privateUnlocked,
                                        biometricEnabled = preferences.isBiometricEnabled,
                                        hasAppPin = preferences.pinHash.isNotBlank(),
                                        onVerifyPin = { pin -> sessionViewModel.verifyAppPin(pin) },
                                        onUnlocked = { vaultViewModel.setPrivateVaultUnlocked(true) },
                                        onLockVault = { vaultViewModel.setPrivateVaultUnlocked(false) },
                                        onSavePrivateNote = { title, secret ->
                                            vaultViewModel.saveManualMemory(
                                                title = title,
                                                content = secret,
                                                memoryType = MemoryTypes.NOTE,
                                                category = "Private",
                                                isPrivate = true,
                                                autoEnrichWithAi = false,
                                                isGuest = session.isGuest
                                            )
                                        },
                                        onUpdatePrivateNote = { id, title, secret ->
                                            vaultViewModel.saveManualMemory(
                                                title = title,
                                                content = secret,
                                                memoryType = MemoryTypes.NOTE,
                                                category = "Private",
                                                isPrivate = true,
                                                autoEnrichWithAi = false,
                                                isGuest = session.isGuest,
                                                existingId = id
                                            )
                                        },
                                        onDeleteMemory = { vaultViewModel.deleteMemory(it) }
                                    )
                                }

                                composable<SubscriptionRoute> {
                                    SubscriptionAndPaymentsScreen(
                                        isGuest = session.isGuest,
                                        subscription = subscription,
                                        entitlement = entitlement,
                                        plans = plans,
                                        orders = orders,
                                        transactions = transactions,
                                        merchantInfo = merchantInfo,
                                        latestCreatedOrder = latestCreatedOrder,
                                        billingMessage = billingMessage,
                                        onSelectPlan = { planId, method -> aiAndBillingViewModel.initiateYoupiPayment(planId, method) },
                                        onOpenUpiUri = { uri -> aiAndBillingViewModel.openUpiUri(uri) },
                                        onOpenCheckoutUrl = { url ->
                                            runCatching {
                                                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                                            }.onFailure {
                                                scope.launch { snackbarHostState.showSnackbar("Unable to open checkout URL in browser.") }
                                            }
                                        },
                                        onSubmitUtr = { ordId, utr -> aiAndBillingViewModel.submitOrderUtr(ordId, utr) },
                                        onRefreshEntitlement = { aiAndBillingViewModel.refreshSubscriptionEntitlement() }
                                    )
                                }

                                composable<BackupRoute> {
                                    BackupAndCloudScreen(
                                        isGuest = session.isGuest,
                                        backupJobs = backupJobs,
                                        backupMessage = backupMessage,
                                        onCreateBackup = { aiAndBillingViewModel.createBackup() },
                                        onRestoreBackup = { path -> aiAndBillingViewModel.restoreLatestBackup(path) },
                                        onDeleteAccountAndData = {
                                            aiAndBillingViewModel.deleteAccountAndData(session.isGuest) {
                                                sessionViewModel.signOut(context)
                                            }
                                        }
                                    )
                                }

                                composable<AdminRoute> {
                                    if (session.isAdmin) {
                                        AdminConfigPanelScreen(
                                            overview = adminOverview,
                                            models = adminModels,
                                            adminMessage = adminMessage,
                                            onRefreshOverview = { aiAndBillingViewModel.loadAdminOverview() },
                                            onSaveGroq = { k, m, tok, f, p ->
                                                aiAndBillingViewModel.saveGroqConfig(k, m, tok, f, p)
                                            },
                                            onFetchGroqModels = { aiAndBillingViewModel.fetchGroqModels() },
                                            onTestGroq = { model -> aiAndBillingViewModel.testGroqConnection(model) },
                                            onSaveYoupi = { u, mid, ak, ws, r, upi, payee, bName, acc, ifsc ->
                                                aiAndBillingViewModel.saveYoupiConfig(u, mid, ak, ws, r, upi, payee, bName, acc, ifsc)
                                            },
                                            onUpdatePlan = { pid, name, price, cred, mb, interval, adFree, active ->
                                                aiAndBillingViewModel.updatePlanPricing(pid, name, price, cred, mb, interval, adFree, active)
                                            },
                                            onDeletePlan = { pid -> aiAndBillingViewModel.deletePlan(pid) },
                                            onUpdateUserSubscription = { uid, tier, adFree, creds, mb, days ->
                                                aiAndBillingViewModel.updateUserSubscription(uid, tier, adFree, creds, mb, days)
                                            },
                                            onReconcilePayment = { oid, status, utr, note ->
                                                aiAndBillingViewModel.reconcilePayment(oid, status, utr, note)
                                            }
                                        )
                                    } else {
                                        ProfileSettingsScreen(
                                            displayName = session.displayName,
                                            email = session.email,
                                            isGuest = session.isGuest,
                                            isAdmin = false,
                                            user = user,
                                            preferences = preferences,
                                            subscription = subscription,
                                            entitlement = entitlement,
                                            memoryCount = memories.size,
                                            reminderCount = reminders.size,
                                            warrantyCount = warranties.size,
                                            receiptCount = receipts.size,
                                            contactCount = contacts.size,
                                            onSetTheme = { sessionViewModel.setThemeMode(it) },
                                            onConfigureLock = { pin, enable, bio ->
                                                sessionViewModel.configureAppLock(pin, enable, bio)
                                            },
                                            onSetBackendUrl = { sessionViewModel.setBackendBaseUrl(it) },
                                            onSetCloudSync = { sessionViewModel.setCloudSyncEnabled(it) },
                                            onTriggerSync = { vaultViewModel.triggerCloudSync() },
                                            onOpenSubscription = { navController.navigate(SubscriptionRoute) },
                                            onOpenBackup = { navController.navigate(BackupRoute) },
                                            onOpenAdmin = {},
                                            onGoogleSignIn = { sessionViewModel.signInWithGoogle(context) },
                                            onSignOut = { sessionViewModel.signOut(context) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
