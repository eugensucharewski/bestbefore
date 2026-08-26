package de.eugens.bestbefore.auth.presentation

import app.cash.turbine.test
import de.eugens.bestbefore.MainDispatcherRule
import de.eugens.bestbefore.auth.domain.use_case.GetCurrentUserEmailUseCase
import de.eugens.bestbefore.auth.domain.use_case.ObserveAuthStateUseCase
import de.eugens.bestbefore.auth.domain.use_case.SignInUseCase
import de.eugens.bestbefore.auth.domain.use_case.SignOutUseCase
import de.eugens.bestbefore.auth.domain.use_case.SignUpUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: AuthViewModel
    private val signInUseCase: SignInUseCase = mockk()
    private val signUpUseCase: SignUpUseCase = mockk()
    private val signOutUseCase: SignOutUseCase = mockk()
    private val observeAuthStateUseCase: ObserveAuthStateUseCase = mockk()
    private val getCurrentUserEmailUseCase: GetCurrentUserEmailUseCase = mockk()

    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

    @Before
    fun setUp() {
        every { observeAuthStateUseCase() } returns authStateFlow
        every { getCurrentUserEmailUseCase() } returns null
        
        viewModel = AuthViewModel(
            signInUseCase,
            signUpUseCase,
            signOutUseCase,
            observeAuthStateUseCase,
            getCurrentUserEmailUseCase
        )
    }

    @Test
    fun `initial state is Unauthenticated when no user email`() = runTest {
        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
    }

    @Test
    fun `initial state is Authenticated when user email exists`() = runTest {
        val email = "test@example.com"
        every { getCurrentUserEmailUseCase() } returns email
        authStateFlow.value = AuthState.Authenticated(email)
        
        val viewModel = AuthViewModel(
            signInUseCase,
            signUpUseCase,
            signOutUseCase,
            observeAuthStateUseCase,
            getCurrentUserEmailUseCase
        )
        
        assertEquals(AuthState.Authenticated(email), viewModel.authState.value)
    }

    @Test
    fun `ToggleMode action toggles isSignUp state`() = runTest {
        assertEquals(false, viewModel.isSignUp.value)
        
        viewModel.onAction(AuthIntent.ToggleMode)
        assertEquals(true, viewModel.isSignUp.value)
        
        viewModel.onAction(AuthIntent.ToggleMode)
        assertEquals(false, viewModel.isSignUp.value)
    }

    @Test
    fun `SignIn action sets Loading state and calls use case`() = runTest {
        // Given
        val email = "test@example.com"
        val password = "password"
        coEvery { signInUseCase(any(), any()) } returns Result.success("id")
        
        viewModel.emailState.edit { replace(0, length, email) }
        viewModel.passwordState.edit { replace(0, length, password) }
        
        // When
        viewModel.onAction(AuthIntent.SignIn)
        
        // Then
        assertEquals(AuthState.Loading, viewModel.authState.value)
        coVerify { signInUseCase(email, password) }
    }

    @Test
    fun `SignIn failure sets Error state`() = runTest {
        // Given
        val errorMessage = "Invalid credentials"
        coEvery { signInUseCase(any(), any()) } returns Result.failure(Exception(errorMessage))
        
        // When
        viewModel.onAction(AuthIntent.SignIn)
        
        // Then
        assertEquals(AuthState.Error(errorMessage), viewModel.authState.value)
    }

    @Test
    fun `SignUp failure sets Error state`() = runTest {
        // Given
        val errorMessage = "User already exists"
        coEvery { signUpUseCase(any(), any()) } returns Result.failure(Exception(errorMessage))
        
        // When
        viewModel.onAction(AuthIntent.SignUp)
        
        // Then
        assertEquals(AuthState.Error(errorMessage), viewModel.authState.value)
    }

    @Test
    fun `SignOut action calls signOutUseCase`() = runTest {
        coEvery { signOutUseCase() } returns Unit
        
        viewModel.onAction(AuthIntent.SignOut)
        
        coVerify { signOutUseCase() }
    }

    @Test
    fun `ResetError action changes Error state back to Unauthenticated`() = runTest {
        coEvery { signInUseCase(any(), any()) } returns Result.failure(Exception("error"))
        viewModel.onAction(AuthIntent.SignIn)
        assertTrue(viewModel.authState.value is AuthState.Error)
        
        viewModel.onAction(AuthIntent.ResetError)
        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
    }

    @Test
    fun `observing auth state updates viewModel authState`() = runTest {
        viewModel.authState.test {
            assertEquals(AuthState.Unauthenticated, awaitItem())
            
            val newState = AuthState.Authenticated("new@example.com")
            authStateFlow.value = newState
            
            assertEquals(newState, awaitItem())
        }
    }
}
