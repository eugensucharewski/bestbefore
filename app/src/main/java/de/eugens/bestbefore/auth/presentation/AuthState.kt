package de.eugens.bestbefore.auth.presentation

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val email: String?) : AuthState()
    data class Error(val message: String) : AuthState()
}
