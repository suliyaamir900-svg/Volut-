const { initializeApp, getApps, applicationDefault } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

function getFirebaseAdmin() {
  if (getApps().length === 0) {
    initializeApp({
      credential: applicationDefault(),
      projectId: process.env.FIREBASE_PROJECT_ID || "gen-lang-client-0401401568",
    });
  }
  const databaseId =
    process.env.FIRESTORE_DATABASE_ID ||
    "ai-studio-android-yaadraks-14ecc900-b5c7-49e7-b38e-db59805a11fc";
  return {
    auth: getAuth(),
    db: getFirestore(databaseId),
  };
}

async function isUserAdmin(db, decodedToken) {
  const bootstrapEmail = (process.env.ADMIN_BOOTSTRAP_EMAIL || "suliyaamir900@gmail.com").toLowerCase();
  if (
    decodedToken.email_verified === true &&
    decodedToken.email &&
    decodedToken.email.toLowerCase() === bootstrapEmail
  ) {
    return true;
  }
  const adminDoc = await db.collection("admins").doc(decodedToken.uid).get();
  return adminDoc.exists;
}

function createAuthMiddleware(deps = {}) {
  return async function requireAuth(req, res, next) {
    try {
      const header = req.headers.authorization || "";
      if (!header.startsWith("Bearer ")) {
        return res.status(401).json({ error: "UNAUTHORIZED", message: "Missing Bearer ID token" });
      }
      const idToken = header.slice(7).trim();
      const { auth, db } = deps.getAdmin ? deps.getAdmin() : getFirebaseAdmin();
      const decoded = await auth.verifyIdToken(idToken);
      req.user = decoded;
      req.db = db;
      req.isAdmin = await isUserAdmin(db, decoded);
      return next();
    } catch (err) {
      return res.status(401).json({ error: "UNAUTHORIZED", message: "Invalid or expired Firebase ID token" });
    }
  };
}

function requireAdmin(req, res, next) {
  if (!req.user || !req.isAdmin) {
    return res.status(403).json({
      error: "FORBIDDEN",
      message: "Server-side admin role required.",
    });
  }
  return next();
}

module.exports = {
  getFirebaseAdmin,
  isUserAdmin,
  createAuthMiddleware,
  requireAdmin,
};
