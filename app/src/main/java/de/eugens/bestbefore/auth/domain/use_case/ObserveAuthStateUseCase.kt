package de.eugens.bestbefore.auth.domain.use_case

import de.eugens.bestbefore.auth.domain.model.AuthState
import de.eugens.bestbefore.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAuthStateUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(): Flow<AuthState> {
        return repository.observeAuthState()
    }
}
