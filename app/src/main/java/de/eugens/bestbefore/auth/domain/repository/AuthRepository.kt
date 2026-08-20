package de.eugens.bestbefore.auth.domain.repository

import de.eugens.bestbefore.auth.presentation.AuthState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val currentUserEmail: String?
    fun observeAuthState(): Flow<AuthState>
    suspend fun signIn(email: String, pass: String): Result<String?>
    suspend fun signUp(email: String, pass: String): Result<String?>
    suspend fun signOut()
}