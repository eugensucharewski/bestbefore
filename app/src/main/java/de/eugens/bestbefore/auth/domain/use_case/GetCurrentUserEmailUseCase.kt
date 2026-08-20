package de.eugens.bestbefore.auth.domain.use_case

import de.eugens.bestbefore.auth.domain.repository.AuthRepository
import javax.inject.Inject

class GetCurrentUserEmailUseCase@Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(): String? {
        return repository.currentUserEmail
    }
}