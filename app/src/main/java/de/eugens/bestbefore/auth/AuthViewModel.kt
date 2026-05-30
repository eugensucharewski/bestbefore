package de.eugens.bestbefore.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val email: String?) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class AuthIntent {
    data class SignIn(val email: String, val pass: String) : AuthIntent()
    data class SignUp(val email: String, val pass: String) : AuthIntent()
    data object SignOut : AuthIntent()
    data object ResetError : AuthIntent()
    data class UpdateEmail(val email: String) : AuthIntent()
    data class UpdatePassword(val password: String) : AuthIntent()
    data object ToggleMode : AuthIntent()
}

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(
        repository.currentUserEmail?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isSignUp = MutableStateFlow(false)
    val isSignUp: StateFlow<Boolean> = _isSignUp.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAuthState().collectLatest { state ->
                _authState.value = state
            }
        }
    }

    fun onAction(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.SignIn -> signIn(intent.email, intent.pass)
            is AuthIntent.SignUp -> signUp(intent.email, intent.pass)
            is AuthIntent.SignOut -> signOut()
            is AuthIntent.ResetError -> resetError()
            is AuthIntent.UpdateEmail -> _email.value = intent.email
            is AuthIntent.UpdatePassword -> _password.value = intent.password
            is AuthIntent.ToggleMode -> _isSignUp.value = !_isSignUp.value
        }
    }

    private fun signIn(email: String, pass: String) {
        _authState.value = AuthState.Loading
        repository.signIn(email, pass,
            onSuccess = { emailResult ->
                _authState.value = AuthState.Authenticated(emailResult)
            },
            onFailure = { message ->
                _authState.value = AuthState.Error(message)
            }
        )
    }

    private fun signUp(email: String, pass: String) {
        _authState.value = AuthState.Loading
        repository.signUp(email, pass,
            onSuccess = { emailResult ->
                _authState.value = AuthState.Authenticated(emailResult)
            },
            onFailure = { message ->
                _authState.value = AuthState.Error(message)
            }
        )
    }

    private fun signOut() {
        repository.signOut()
    }

    private fun resetError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }
}
