package com.docs.scanner.domain.model

import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainException
import com.docs.scanner.domain.core.DomainResult

/**
 * Legacy Result type used by some presentation code.
 *
 * Prefer using [DomainResult] in new code; this exists for compatibility while
 * ViewModels are being migrated.
 */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val exception: Throwable) : Result<Nothing>
    data object Loading : Result<Nothing>
}

fun <T> DomainResult<T>.toLegacyResult(): Result<T> = when (this) {
    is DomainResult.Success -> Result.Success(data)
    is DomainResult.Failure -> Result.Error(DomainException(error))
}

fun Result.Error.domainErrorOrNull(): DomainError? =
    (exception as? DomainException)?.error