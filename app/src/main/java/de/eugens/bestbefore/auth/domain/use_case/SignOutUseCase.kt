package de.eugens.bestbefore.auth.domain.use_case

import de.eugens.bestbefore.auth.domain.repository.AuthRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke() {
        return repository.signOut()
    }
}