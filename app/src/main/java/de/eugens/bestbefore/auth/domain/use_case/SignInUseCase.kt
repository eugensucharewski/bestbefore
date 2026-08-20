package de.eugens.bestbefore.auth.domain.use_case

import de.eugens.bestbefore.auth.domain.repository.AuthRepository
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, pass: String): Result<String?> {
        return repository.signIn(email, pass)
    }
}