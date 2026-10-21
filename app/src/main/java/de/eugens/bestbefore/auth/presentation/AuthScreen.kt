package de.eugens.bestbefore.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.eugens.bestbefore.R
import de.eugens.bestbefore.auth.domain.model.AuthState

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val authState by viewModel.authState.collectAsState()
    val isSignUp by viewModel.isSignUp.collectAsState()
    val title by viewModel.title.collectAsState()
    val buttonText by viewModel.buttonText.collectAsState()
    val toggleText by viewModel.toggleText.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title.asString(),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            state = viewModel.emailState,
            label = { Text(stringResource(R.string.email)) },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedSecureTextField(
            state = viewModel.passwordState,
            label = {
                Text(stringResource(R.string.password))
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (authState is AuthState.Loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (isSignUp) viewModel.onAction(AuthIntent.SignUp)
                    else viewModel.onAction(AuthIntent.SignIn)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText.asString())
            }
            TextButton(onClick = { viewModel.onAction(AuthIntent.ToggleMode) }) {
                Text(toggleText.asString())
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
