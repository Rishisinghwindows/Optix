package com.optix.app.presentation.screens.auth

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.optix.app.core.constants.ApiConstants

/**
 * Helper class for Google Sign-In integration
 */
class GoogleSignInHelper(
    private val context: Context
) {
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(ApiConstants.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()

        GoogleSignIn.getClient(context, gso)
    }

    /**
     * Get the sign-in intent to launch
     */
    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    /**
     * Handle the sign-in result
     * @return ID token if successful, null otherwise
     */
    fun handleSignInResult(task: Task<GoogleSignInAccount>): Result<String> {
        return try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                Result.success(idToken)
            } else {
                Result.failure(Exception("No ID token received"))
            }
        } catch (e: ApiException) {
            Result.failure(Exception("Google Sign-In failed: ${e.statusCode}"))
        }
    }

    /**
     * Sign out from Google
     */
    fun signOut(onComplete: () -> Unit = {}) {
        googleSignInClient.signOut().addOnCompleteListener { onComplete() }
    }

    /**
     * Revoke access (disconnect)
     */
    fun revokeAccess(onComplete: () -> Unit = {}) {
        googleSignInClient.revokeAccess().addOnCompleteListener { onComplete() }
    }

    /**
     * Check if user is already signed in
     */
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    companion object {
        /**
         * Parse sign-in result from activity result
         */
        fun getSignedInAccountFromIntent(data: Intent?): Task<GoogleSignInAccount> {
            return GoogleSignIn.getSignedInAccountFromIntent(data)
        }
    }
}

/**
 * Extension function to launch Google Sign-In
 */
fun ActivityResultLauncher<Intent>.launchGoogleSignIn(helper: GoogleSignInHelper) {
    launch(helper.getSignInIntent())
}
