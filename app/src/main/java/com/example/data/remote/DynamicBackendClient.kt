package com.example.data.remote

import com.example.core.datastore.UserPreferencesStore
import com.google.firebase.auth.FirebaseAuth
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicBackendClient @Inject constructor(
    private val preferencesStore: UserPreferencesStore,
    private val firebaseAuth: FirebaseAuth
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = runCatching {
            val user = firebaseAuth.currentUser
            if (user != null) {
                runBlocking { user.getIdToken(false).await().token }
            } else {
                null
            }
        }.getOrNull()

        val reqBuilder = original.newBuilder()
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $token")
        }
        chain.proceed(reqBuilder.build())
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    @Volatile
    private var cachedUrl: String? = null

    @Volatile
    private var cachedService: BackendApiService? = null

    suspend fun getService(): BackendApiService {
        val baseUrl = preferencesStore.preferencesFlow.first().backendBaseUrl
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val existing = cachedService
        if (existing != null && cachedUrl == normalized) {
            return existing
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(BackendApiService::class.java).also {
            cachedUrl = normalized
            cachedService = it
        }
    }

    suspend fun isBackendPlaceholderUrl(): Boolean {
        val url = preferencesStore.preferencesFlow.first().backendBaseUrl
        return url.contains("yaadrakshak.local") || url.isBlank()
    }
}
