package de.eugens.bestbefore.auth.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

import androidx.compose.ui.res.stringResource
import de.eugens.bestbefore.R

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val isSignUp by viewModel.isSignUp.collectAsState()
    val authState by viewModel.authState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSignUp) stringResource(R.string.sign_up_title) else stringResource(R.string.sign_in_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { viewModel.onAction(AuthIntent.UpdateEmail(it)) },
            label = { Text(stringResource(R.string.email)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { viewModel.onAction(AuthIntent.UpdatePassword(it)) },
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (authState is AuthState.Loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (isSignUp) viewModel.onAction(AuthIntent.SignUp(email, password))
                    else viewModel.onAction(AuthIntent.SignIn(email, password))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSignUp) stringResource(R.string.sign_up_button) else stringResource(R.string.sign_in_button))
            }
            TextButton(onClick = { viewModel.onAction(AuthIntent.ToggleMode) }) {
                Text(if (isSignUp) stringResource(R.string.already_have_account) else stringResource(
                    R.string.no_account))
            }
        }

        if (authState is AuthState.Error) {
            Text(
                text = (authState as AuthState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(onClick = { viewModel.onAction(AuthIntent.ResetError) }) {
                Text(stringResource(R.string.try_again))
            }
        }
    }
}
