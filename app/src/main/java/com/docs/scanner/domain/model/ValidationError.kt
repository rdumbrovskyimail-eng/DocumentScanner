package com.docs.scanner.domain.model

/**
 * Represents validation errors in the domain layer.
 */
sealed class ValidationError {
    /**
     * Invalid input with detailed context
     * @param field Field name that failed validation
     * @param value The invalid value
     * @param reason Why validation failed
     */
    data class InvalidInput(
        val field: String,
        val value: String,
        val reason: String
    ) : ValidationError()
    
    data class EmptyField(val fieldName: String) : ValidationError()
    
    /**
     * Name exceeds maximum length
     * @param actualLength Current length
     * @param maxLength Maximum allowed length
     */
    data class NameTooLong(
        val actualLength: Int,
        val maxLength: Int
    ) : ValidationError()
    
    data object DueDateInPast : ValidationError()
    data object Unknown : ValidationError()
}