package de.eugens.bestbefore.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.eugens.bestbefore.auth.domain.use_case.GetCurrentUserEmailUseCase
import de.eugens.bestbefore.auth.domain.use_case.ObserveAuthStateUseCase
import de.eugens.bestbefore.auth.domain.use_case.SignInUseCase
import de.eugens.bestbefore.auth.domain.use_case.SignOutUseCase
import de.eugens.bestbefore.auth.domain.use_case.SignUpUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInUseCase: SignInUseCase,
    private val signUpUseCase: SignUpUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    getCurrentUserEmailUseCase: GetCurrentUserEmailUseCase
) : ViewModel() {

    companion object {
        const val UNKNOWN_ERROR = "unknown error"
    }

    private val _authState = MutableStateFlow(
        getCurrentUserEmailUseCase()?.let { AuthState.Authenticated(it) }
            ?: AuthState.Unauthenticated
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
            observeAuthStateUseCase().collectLatest { state ->
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
        viewModelScope.launch {
            val signInResult = signInUseCase(email, pass)
            if (signInResult.isFailure) {
                _authState.value = AuthState.Error(
                    signInResult.exceptionOrNull()?.localizedMessage ?: UNKNOWN_ERROR
                )
            }
        }
    }

    private fun signUp(email: String, pass: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val signUpResult = signUpUseCase(email, pass)
            if (signUpResult.isFailure) {
                _authState.value = AuthState.Error(
                    signUpResult.exceptionOrNull()?.localizedMessage ?: UNKNOWN_ERROR
                )
            }
        }
    }

    private fun signOut() {
        viewModelScope.launch { 
            signOutUseCase()
        }
    }

    private fun resetError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }
}
