package com.docs.scanner.domain.model

sealed class ValidationError {
    data class InvalidInput(val message: String) : ValidationError()
    data class EmptyField(val fieldName: String) : ValidationError()
    object Unknown : ValidationError()
}