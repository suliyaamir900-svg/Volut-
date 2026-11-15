package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_and_observeUserProfile_succeedForAuthenticatedOwner() = runBlocking {
        val email = "alice_${UUID.randomUUID().toString().take(6)}@test.com"
        val uid = signInTestUser(email)
        val repo = CloudSyncRepository(firestore, auth)

        val profileRes = withTimeout(5000L) {
            repo.ensureUserProfile("Alice", email, null)
        }
        assertTrue("ensureUserProfile failed: ${profileRes.exceptionOrNull()}", profileRes.isSuccess)
        assertEquals("user", profileRes.getOrNull()?.role)

        val observed = withTimeout(4000L) {
            repo.observeUserProfile().first { it != null }
        }
        assertEquals(uid, observed?.userId)
        assertEquals("Alice", observed?.displayName)
    }

    @Test
    fun personalVaultSubcollection_isDeniedInFirestoreEvenForAuthenticatedOwner() = runBlocking {
        val uid = signInTestUser("vault_${UUID.randomUUID().toString().take(6)}@test.com")
        val thrown = try {
            withTimeout(4000L) {
                firestore.collection("users").document(uid)
                    .collection("memories").document("mem_local_only")
                    .set(mapOf("userId" to uid, "title" to "Test"))
                    .await()
            }
            null
        } catch (e: Exception) {
            e
        }
        val fsEx = thrown?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun observeUserProfile_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CloudSyncRepository(firestore, auth)

        val thrown = try {
            withTimeout(3000L) {
                repo.observeUserProfile().first()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertTrue(thrown != null)
        val fsEx = thrown?.unwrapFirestoreException()
        if (fsEx != null) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        } else {
            assertTrue(thrown is IllegalStateException)
        }
    }

    @Test
    fun appConfig_groqIsDeniedToStandardUser_whilePaymentsPublicIsReadable() = runBlocking {
        signInTestUser("reader_${UUID.randomUUID().toString().take(6)}@test.com")
        val groqErr = try {
            withTimeout(4000L) {
                firestore.collection("app_config").document("groq").get().await()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, groqErr?.unwrapFirestoreException()?.code)

        val publicDoc = withTimeout(4000L) {
            firestore.collection("app_config").document("payments_public").get().await()
        }
        assertTrue(publicDoc != null)
    }
}
