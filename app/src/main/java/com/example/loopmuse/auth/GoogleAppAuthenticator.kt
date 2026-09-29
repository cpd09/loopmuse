package com.example.loopmuse.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** The existing anonymous Lounge identity is linked when possible so its posts keep their UID. */
class GoogleAppAuthenticator(private val activity: Activity) {
    val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    val isConfigured: Boolean
        get() = webClientId() != null

    fun hasGoogleSession(): Boolean = firebaseAuth.currentUser?.let { user ->
        !user.isAnonymous && user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
    } == true

    suspend fun signIn(): Result<Unit> = runCatching {
        val clientId = webClientId()
            ?: error("Google 로그인 설정이 아직 완료되지 않았습니다. Firebase 설정 파일을 확인해 주세요.")
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(activity).getCredential(activity, request)
        val credential = response.credential
        require(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google 계정 정보를 읽지 못했습니다. 다시 시도해 주세요."
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val existingAnonymousUser = firebaseAuth.currentUser?.takeIf { it.isAnonymous }
        if (existingAnonymousUser != null) {
            try {
                existingAnonymousUser.linkWithCredential(firebaseCredential).await()
            } catch (collision: FirebaseAuthUserCollisionException) {
                // The Google account already exists. The old anonymous UID stays on the server.
                firebaseAuth.signInWithCredential(firebaseCredential).await()
            }
        } else {
            firebaseAuth.signInWithCredential(firebaseCredential).await()
        }
        check(hasGoogleSession()) { "Google 로그인을 확인하지 못했습니다. 다시 시도해 주세요." }
    }

    private fun webClientId(): String? {
        val id = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        return if (id == 0) null else activity.getString(id).takeIf { it.isNotBlank() }
    }
}
