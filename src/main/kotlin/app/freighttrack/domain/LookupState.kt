package app.freighttrack.domain

enum class LookupState {
    PENDING,
    LOADING,
    LOADED,
    NOT_FOUND,
    ERROR
}
