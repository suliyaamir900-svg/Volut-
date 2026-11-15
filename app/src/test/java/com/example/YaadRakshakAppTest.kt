package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.crypto.VaultCryptoManager
import com.example.core.datastore.AppPreferences
import com.example.core.datastore.ThemeMode
import com.example.data.local.AiConversationEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.FolderEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.MemoryTypes
import com.example.data.local.PaymentOrderEntity
import com.example.data.local.PersonalContactEntity
import com.example.data.local.ReceiptEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SyncStates
import com.example.data.local.UserEntity
import com.example.data.local.WarrantyEntity
import com.example.data.local.YaadRakshakDatabase
import com.example.data.remote.FirestorePlan
import com.example.data.remote.MerchantPublicInfoDto
import com.example.ui.screens.AuthChoiceScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SubscriptionAndPaymentsScreen
import com.example.ui.theme.YaadRakshakTheme
import com.example.ui.viewmodel.EntitlementUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class YaadRakshakAppTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: YaadRakshakDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, YaadRakshakDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun cryptoManager_pinHashingAndSha256_areDeterministicAndSalted() {
        val crypto = VaultCryptoManager()
        val pin1 = crypto.hashPin("4821")
        val pin2 = crypto.hashPin("4821")
        val pinOther = crypto.hashPin("9999")
        assertEquals(pin1, pin2)
        assertNotEquals(pin1, pinOther)
        assertEquals(64, pin1.length)

        val digest = crypto.sha256Hex("YaadRakshak Backup Payload")
        assertEquals(64, digest.length)
        assertEquals(digest, crypto.sha256Hex("YaadRakshak Backup Payload"))
    }

    @Test
    fun roomDatabase_insertsAndQueriesAllCoreVaultModules() = runTest {
        val uid = "user_test_1"
        db.userDao().upsertUser(UserEntity(uid = uid, displayName = "Test User", isGuest = true))
        db.memoryDao().upsertFolder(FolderEntity(id = "f1", userId = uid, name = "Tax & Finance"))
        db.memoryDao().upsertMemory(
            MemoryEntity(
                id = "m1",
                userId = uid,
                title = "MacBook Pro Invoice",
                content = "Apple India Pvt Ltd ₹1,69,900 GSTIN 29AABCA",
                memoryType = MemoryTypes.RECEIPT,
                folderId = "f1",
                categoryName = "Finance",
                tagsCsv = "laptop,apple"
            )
        )
        db.lifeTrackerDao().upsertReminder(
            ReminderEntity(
                id = "r1",
                userId = uid,
                title = "Renew AppleCare",
                dueAtMillis = System.currentTimeMillis() + 86_400_000L,
                priority = "HIGH"
            )
        )
        db.lifeTrackerDao().upsertWarranty(
            WarrantyEntity(
                id = "w1",
                userId = uid,
                productName = "MacBook Pro 14",
                brand = "Apple",
                serialNumber = "C02XYZ",
                purchaseDateMillis = System.currentTimeMillis(),
                expiryDateMillis = System.currentTimeMillis() + 365L * 86_400_000L
            )
        )
        db.lifeTrackerDao().upsertReceipt(
            ReceiptEntity(
                id = "rec1",
                userId = uid,
                merchantName = "Apple Saket",
                totalAmountPaise = 16_990_000L,
                purchasedAtMillis = System.currentTimeMillis()
            )
        )
        db.lifeTrackerDao().upsertExpense(
            ExpenseEntity(
                id = "exp1",
                userId = uid,
                title = "Laptop Purchase",
                category = "Electronics",
                amountPaise = 16_990_000L,
                spentAtMillis = System.currentTimeMillis()
            )
        )
        db.lifeTrackerDao().upsertContact(
            PersonalContactEntity(
                id = "c1",
                userId = uid,
                name = "Dr. Sharma",
                relationOrRole = "Physician",
                phone = "+91-9876543210"
            )
        )

        val memories = db.memoryDao().observeMemories(uid).first()
        val reminders = db.lifeTrackerDao().observeReminders(uid).first()
        val warranties = db.lifeTrackerDao().observeWarranties(uid).first()
        val receipts = db.lifeTrackerDao().observeReceipts(uid).first()
        val expenses = db.lifeTrackerDao().observeExpenses(uid).first()
        val contacts = db.lifeTrackerDao().observeContacts(uid).first()

        assertEquals(1, memories.size)
        assertEquals("MacBook Pro Invoice", memories.first().title)
        assertEquals(1, reminders.size)
        assertEquals("HIGH", reminders.first().priority)
        assertEquals(1, warranties.size)
        assertEquals("C02XYZ", warranties.first().serialNumber)
        assertEquals(1, receipts.size)
        assertEquals(16_990_000L, expenses.first().amountPaise)
        assertEquals("Dr. Sharma", contacts.first().name)
    }

    @Test
    fun onboardingScreen_rendersPrimaryActionsAndTriggersGuestCallback() {
        var guestClicked = false
        var nextClickedCount = 0

        composeRule.setContent {
            YaadRakshakTheme {
                OnboardingScreen(
                    onFinish = { nextClickedCount++ },
                    onContinueGuest = { guestClicked = true }
                )
            }
        }

        composeRule.onNodeWithText("Your Entire Life, Offline-First").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_guest_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(guestClicked)
    }

    @Test
    fun authChoiceScreen_triggersGoogleSignInCallback() {
        var googleClicked = false
        composeRule.setContent {
            YaadRakshakTheme {
                AuthChoiceScreen(
                    isLoading = false,
                    errorMessage = null,
                    onGoogleSignIn = { googleClicked = true },
                    onContinueGuest = {}
                )
            }
        }

        composeRule.onNodeWithTag("google_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(googleClicked)
    }

    @Test
    fun defaults_themeIsLightAndVaultSyncStateIsLocalOnly() {
        val prefs = AppPreferences()
        assertEquals(ThemeMode.LIGHT, prefs.themeMode)

        val mem = MemoryEntity(
            id = "m_local",
            userId = "u1",
            title = "Local Note",
            content = "Stored strictly in Room",
            memoryType = MemoryTypes.NOTE
        )
        assertEquals(SyncStates.LOCAL_ONLY, mem.syncStatus)
    }

    @Test
    fun roomDatabase_fullCrudUpdateDeleteAndPaymentOrderUtrPreservation() = runTest {
        val uid = "user_crud"
        db.userDao().upsertUser(UserEntity(uid = uid, displayName = "CRUD User", adFree = true))
        val folder = FolderEntity(id = "fld_1", userId = uid, name = "Legal")
        db.memoryDao().upsertFolder(folder)

        val mem = MemoryEntity(id = "mem_1", userId = uid, title = "Draft", content = "V1", memoryType = MemoryTypes.NOTE)
        db.memoryDao().upsertMemory(mem)
        db.memoryDao().upsertMemory(mem.copy(title = "Final", content = "V2"))
        assertEquals("Final", db.memoryDao().getMemoryById("mem_1")?.title)

        db.memoryDao().deleteMemoryById("mem_1")
        db.memoryDao().deleteFolder("fld_1")
        assertTrue(db.memoryDao().observeMemories(uid).first().isEmpty())
        assertTrue(db.memoryDao().observeFolders(uid).first().isEmpty())

        db.aiAndBillingDao().upsertConversation(AiConversationEntity(id = "conv_1", userId = uid, title = "Test Chat"))
        assertEquals(1, db.aiAndBillingDao().observeConversations(uid).first().size)
        db.aiAndBillingDao().deleteConversationById("conv_1")
        assertTrue(db.aiAndBillingDao().observeConversations(uid).first().isEmpty())

        db.aiAndBillingDao().upsertPaymentOrder(
            PaymentOrderEntity(
                id = "ord_123",
                userId = uid,
                planId = "monthly",
                amountPaise = 9900L,
                idempotencyKey = "idem_1",
                status = "PENDING_PAYMENT",
                paymentMethod = "DIRECT_UPI"
            )
        )
        db.aiAndBillingDao().updatePaymentOrderUtrAndStatus("ord_123", "PENDING_RECONCILIATION", "412345678901")
        val updatedOrder = db.aiAndBillingDao().getPaymentOrderById("ord_123")
        assertEquals("monthly", updatedOrder?.planId)
        assertEquals(9900L, updatedOrder?.amountPaise)
        assertEquals("PENDING_RECONCILIATION", updatedOrder?.status)
        assertEquals("412345678901", updatedOrder?.utrReference)
    }

    @Test
    fun homeDashboard_darkTheme_showsAdBannerOnFreeAndHidesWhenAdFree() {
        composeRule.setContent {
            YaadRakshakTheme(themeMode = ThemeMode.DARK) {
                HomeDashboardScreen(
                    displayName = "Aamir",
                    isGuest = false,
                    user = UserEntity(uid = "u1", displayName = "Aamir", adFree = false, aiCreditsRemaining = 15),
                    subscription = null,
                    entitlement = EntitlementUiState(planId = "free", planName = "Free Plan", adFree = false, aiCreditsRemaining = 15, aiMonthlyCredits = 20L),
                    memories = emptyList(),
                    reminders = emptyList(),
                    warranties = emptyList(),
                    expenses = emptyList(),
                    onQuickScanDoc = {},
                    onQuickImportFile = {},
                    onQuickVoiceNote = {},
                    onQuickAskAi = {},
                    onQuickAddExpense = {},
                    onOpenPrivateVault = {},
                    onOpenMemories = {},
                    onOpenReminders = {},
                    onOpenUpgrade = {},
                    onMemoryClick = {}
                )
            }
        }
        composeRule.onNodeWithText("Namaste, Aamir").assertIsDisplayed()
        composeRule.onNodeWithTag("free_tier_ad_banner").assertIsDisplayed()
    }

    @Test
    fun homeDashboard_adFreeTrue_blocksAdsCompletelyAndShowsBadge() {
        composeRule.setContent {
            YaadRakshakTheme(themeMode = ThemeMode.DARK) {
                HomeDashboardScreen(
                    displayName = "Pro User",
                    isGuest = false,
                    user = UserEntity(uid = "u2", displayName = "Pro User", adFree = true, aiCreditsRemaining = 480),
                    subscription = null,
                    entitlement = EntitlementUiState(planId = "monthly", planName = "Premium Monthly", adFree = true, aiCreditsRemaining = 480, aiMonthlyCredits = 500L),
                    memories = emptyList(),
                    reminders = emptyList(),
                    warranties = emptyList(),
                    expenses = emptyList(),
                    onQuickScanDoc = {},
                    onQuickImportFile = {},
                    onQuickVoiceNote = {},
                    onQuickAskAi = {},
                    onQuickAddExpense = {},
                    onOpenPrivateVault = {},
                    onOpenMemories = {},
                    onOpenReminders = {},
                    onOpenUpgrade = {},
                    onMemoryClick = {}
                )
            }
        }
        composeRule.onNodeWithText("ADS BLOCKED ✓").assertIsDisplayed()
        composeRule.onNodeWithTag("free_tier_ad_banner").assertDoesNotExist()
    }

    @Test
    fun subscriptionScreen_darkTheme_displaysMerchantUpiAndPlans() {
        composeRule.setContent {
            YaadRakshakTheme(themeMode = ThemeMode.DARK) {
                SubscriptionAndPaymentsScreen(
                    isGuest = false,
                    subscription = null,
                    entitlement = EntitlementUiState(planId = "free", planName = "Free Plan", adFree = false),
                    plans = listOf(
                        FirestorePlan(planId = "free", name = "Free Plan", priceInr = 0L, interval = "free", aiMonthlyCredits = 20L, storageLimitMb = 100L, adFree = false, active = true),
                        FirestorePlan(planId = "monthly", name = "Premium Monthly", priceInr = 99L, interval = "monthly", aiMonthlyCredits = 500L, storageLimitMb = 5120L, adFree = true, active = true)
                    ),
                    orders = emptyList(),
                    transactions = emptyList(),
                    merchantInfo = MerchantPublicInfoDto(
                        paymentUpiId = "aamir@okaxis",
                        payeeName = "YaadRakshak",
                        upiReady = true
                    ),
                    billingMessage = null,
                    onSelectPlan = { _, _ -> },
                    onRefreshEntitlement = {}
                )
            }
        }
        composeRule.onNodeWithTag("subscription_plans_list")
            .performScrollToNode(hasText("• Direct UPI: aamir@okaxis (YaadRakshak)"))
        composeRule.onNodeWithText("• Direct UPI: aamir@okaxis (YaadRakshak)").assertIsDisplayed()
        composeRule.onNodeWithTag("subscription_plans_list")
            .performScrollToNode(hasText("Premium Monthly"))
        composeRule.onNodeWithText("Premium Monthly").assertIsDisplayed()
    }
}
