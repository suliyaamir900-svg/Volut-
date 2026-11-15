# YaadRakshak — Testing, Backend Configuration, Signed APK & Play Store AAB Guide

## 1. Architecture & Security Posture
- **100% Local Offline-First Vault**: Room SQLite (`YaadRakshakDatabase`) stores **all** personal memories, folders, tags, file attachments, reminders, voice notes, receipts, warranties, expenses, contacts, AI chat citations, and encrypted backup records strictly on-device (`SyncStates.LOCAL_ONLY`). Personal vault records are never uploaded to Firebase Firestore.
- **Firebase Auth, Role & Billing Only**: Firebase Auth + Firestore store only account identity (`/users/{uid}`), Admin RBAC (`/admins/{uid}` with `suliyaamir900@gmail.com` auto-bootstrap), subscription plans (`/plans`), and server-verified billing records. Client writes to personal vault subcollections are explicitly denied in `firestore.rules`.
- **Hardware-Backed Encryption**: `VaultCryptoManager` uses Android Keystore (`AES/GCM/NoPadding`, 256-bit) to encrypt Private Vault entries and `.yrbak` backup archives, plus SHA-256 checksums and PIN hashing.
- **Unified Auth for Users & Admin**: Exclusively Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) + Firebase Auth (signing in with `suliyaamir900@gmail.com` automatically unlocks the Admin Control Center), alongside a purely local **Guest Mode** that never touches Firebase.
- **Pure White & Saffron Orange Design System**: Material 3 Pure White (`#FFFFFF`) and Saffron Orange (`#EA580C` / `#F97316`) theme by default with an optional dark theme toggle and custom Saffron adaptive launcher icon.
- **Zero Client-Side Secrets**: Neither Groq API keys nor YouPI PG merchant/webhook secrets exist anywhere in Android source code, `.env`, or the compiled APK. All AI and payment operations run through the authenticated Node.js backend (`backend/`).

---

## 2. Backend Deployment & Environment Setup (`backend/`)
1. Navigate to `backend/` and install dependencies:
   ```bash
   cd backend
   npm install
   ```
2. Copy `backend/.env.example` to `backend/.env` and configure:
   - `FIREBASE_SERVICE_ACCOUNT_JSON` (service account JSON for Firebase Admin SDK)
   - `FIRESTORE_DATABASE_ID=ai-studio-android-yaadraks-14ecc900-b5c7-49e7-b38e-db59805a11fc`
   - `GROQ_API_KEY` (from [console.groq.com](https://console.groq.com))
   - `YOUPI_API_URL`, `YOUPI_MERCHANT_ID`, `YOUPI_MERCHANT_API_KEY`, `YOUPI_WEBHOOK_SECRET`, `YOUPI_RETURN_URL`
   - `ADMIN_BOOTSTRAP_EMAIL=suliyaamir900@gmail.com`
3. Deploy to HTTPS hosting (e.g., Cloud Run, Render, Fly.io) and register your public webhook endpoint in YouPI PG Dashboard:
   - `POST https://<your-backend-host>/api/payments/webhook`
4. Set `BACKEND_BASE_URL` in the Secrets panel (or update inside the app under **Profile -> Backend API Endpoint**).
5. Sign in with `suliyaamir900@gmail.com` to access the **Admin Control Center**, fetch `/openai/v1/models`, test Groq connectivity, manage YouPI PG config, and adjust Monthly (`₹99`) / Yearly (`₹799`) plans.

---

## 3. Automated Testing Matrix

| Layer | Suite | Scope | Command |
| :--- | :--- | :--- | :--- |
| Firestore Rules (JS) | `firestore.test.js` | Zero-trust owner isolation, `suliyaamir900@gmail.com` admin gate, client-write blocks on server collections (`paymentTransactions`, `subscriptions`) AND personal vault subcollections (`memories`, `warranties`) | `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js` |
| Backend API & Webhooks | `backend/test/backend.test.js` | HMAC-SHA256 YouPI webhook signature verification, unconfigured Groq/YouPI structured 503 responses, `MANUAL_UPI` order payload formatting | `node --test backend/test/backend.test.js` |
| Kotlin Firestore Rule Test | `CloudSyncRepositoryRuleTest` | Authenticated owner Auth profile upsert/observe, unauthenticated rejection, and `PERMISSION_DENIED` on `/users/{uid}/memories` | `gradle :app:testDebugUnitTest --tests '*Rule*'` |
| Android Core & UI Robolectric | `YaadRakshakAppTest` | Keystore/AES fallback crypto roundtrip, PIN hashing, Room CRUD & search across memories/warranties/receipts/expenses/contacts, Compose onboarding & auth screen CUJ | `gradle :app:testDebugUnitTest` |

> **Important Note on Live Integrations**: Automated tests verify schema rules, Room persistence, cryptographic integrity, webhook HMAC validation, and fail-closed unconfigured error states. Live end-to-end Groq completions and real-money YouPI PG UPI transactions require providing live `GROQ_API_KEY` and `YOUPI_*` credentials on the deployed HTTPS backend.

---

## 4. Generating Release-Ready Signed APK & Google Play AAB

### Step 1: Create an Upload Keystore (for Play App Signing)
```bash
keytool -genkey -v -keystore release-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```
Register the upload keystore SHA-1/SHA-256 fingerprints in Firebase Console (in addition to the debug fingerprint already provisioned) so Google Sign-In works on release builds.

### Step 2: Export Signing Environment Variables
```bash
export KEYSTORE_PATH=/absolute/path/to/release-upload-key.jks
export STORE_PASSWORD="<your-keystore-password>"
export KEY_PASSWORD="<your-key-password>"
```

### Step 3: Build Release Signed APK (Direct Verification)
```bash
gradle :app:assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

### Step 4: Build Google Play Store Android App Bundle (`.aab`)
```bash
gradle :app:bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`

### Play Store Readiness Checklist
- Target SDK `36` configured (`minSdk 24`, `compileSdk 36`).
- Zero-permission Android Photo Picker (`PickVisualMedia`) and Storage Access Framework (`OpenDocument`) used instead of broad `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` permissions.
- `CAMERA`, `RECORD_AUDIO`, and `POST_NOTIFICATIONS` requested strictly just-in-time at feature entry.
- Complete account & data deletion flow built into **Backup & Cloud Sync -> Delete All My Data**.
- Custom adaptive launcher icon and edge-to-edge layout implemented.
