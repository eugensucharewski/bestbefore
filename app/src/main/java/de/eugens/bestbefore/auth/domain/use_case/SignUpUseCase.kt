package de.eugens.bestbefore.auth.domain.use_case

import de.eugens.bestbefore.auth.domain.repository.AuthRepository
import javax.inject.Inject

class SignUpUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, pass: String): Result<String?> {
        return repository.signUp(email, pass)
    }
}