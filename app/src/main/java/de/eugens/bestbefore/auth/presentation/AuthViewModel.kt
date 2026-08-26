package de.eugens.bestbefore.auth.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.eugens.bestbefore.auth.domain.use_case.GetCurrentUserEmailUseCase
import de.eugens.bestbefore.auth.domain.use_case.ObserveAuthStateUseCase
import de.eugens.bestbefore.auth.domain.use_case.SignInUseCase
import de.eugens.bestbefore.auth.domain.use_case.SignOutUseCase
import de.eugens.bestbefore.auth.domain.use_case.SignUpUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import de.eugens.bestbefore.R
import de.eugens.bestbefore.common.UiText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


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

    private val _isSignUp = MutableStateFlow(false)
    val isSignUp: StateFlow<Boolean> = _isSignUp.asStateFlow()

    val title = isSignUp.map { isSignUp ->
        if (isSignUp) UiText.StringResource(R.string.sign_up_title)
        else UiText.StringResource(R.string.sign_in_title)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiText.Empty)

    val buttonText = isSignUp.map { isSignUp ->
        if (isSignUp) UiText.StringResource(R.string.sign_up_button)
        else UiText.StringResource(R.string.sign_in_button)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiText.Empty)

    val toggleText = isSignUp.map { isSignUp ->
        if (isSignUp) UiText.StringResource(R.string.already_have_account)
        else UiText.StringResource(R.string.no_account)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiText.Empty)

    val emailState = TextFieldState()
    val passwordState = TextFieldState()


    init {
        viewModelScope.launch {
            observeAuthStateUseCase().collectLatest { state ->
                _authState.value = state
            }
        }
    }

    fun onAction(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.SignIn -> signIn()
            is AuthIntent.SignUp -> signUp()
            is AuthIntent.SignOut -> signOut()
            is AuthIntent.ResetError -> resetError()
            is AuthIntent.ToggleMode -> _isSignUp.value = !_isSignUp.value
        }
    }

    private fun signIn() {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val signInResult = signInUseCase(
                emailState.text.toString(),
                passwordState.text.toString()
            )
            if (signInResult.isFailure) {
                _authState.value = AuthState.Error(
                    signInResult.exceptionOrNull()?.localizedMessage ?: UNKNOWN_ERROR
                )
            }
        }
    }

    private fun signUp() {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val signUpResult = signUpUseCase(
                emailState.text.toString(),
                passwordState.text.toString()
            )
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
