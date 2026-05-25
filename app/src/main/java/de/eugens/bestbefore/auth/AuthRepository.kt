package de.eugens.bestbefore.auth

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AuthRepository {
    private val auth: FirebaseAuth = Firebase.auth

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    fun observeAuthState(): Flow<AuthState> = callbackFlow {
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

    fun signIn(email: String, pass: String, onSuccess: (String?) -> Unit, onFailure: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { onSuccess(it.user?.email) }
            .addOnFailureListener { onFailure(it.localizedMessage ?: "Login failed") }
    }

    fun signUp(email: String, pass: String, onSuccess: (String?) -> Unit, onFailure: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { onSuccess(it.user?.email) }
            .addOnFailureListener { onFailure(it.localizedMessage ?: "Registration failed") }
    }

    fun signOut() {
        auth.signOut()
    }
}