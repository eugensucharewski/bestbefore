package de.eugens.bestbefore.auth.presentation

sealed class AuthIntent {
    data object SignIn : AuthIntent()
    data object SignUp : AuthIntent()
    data object SignOut : AuthIntent()
    data object ResetError : AuthIntent()
    data object ToggleMode : AuthIntent()
}