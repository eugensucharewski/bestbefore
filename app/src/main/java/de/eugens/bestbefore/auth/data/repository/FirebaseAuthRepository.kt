package de.eugens.bestbefore.auth.data.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import de.eugens.bestbefore.auth.domain.repository.AuthRepository
import de.eugens.bestbefore.auth.presentation.AuthState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(): AuthRepository {
    private val auth: FirebaseAuth = Firebase.auth

    override val currentUserEmail: String?
        get() = auth.currentUser?.email

    override fun observeAuthState(): Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                trySend(AuthState.Authenticated(user.email))
            } else {
                trySend(AuthState.Unauthenticated)
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, pass: String): Result<String?> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, pass).await()
        result.user?.email
    }

    override suspend fun signUp(email: String, pass: String): Result<String?> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, pass).await()
        result.user?.email
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}