package dev.necr0manthre.innotournament.teams

enum class PlayerKickResult {
    SUCCESS,
    CANCELLED,
    NOT_IN_TEAM;

    val isSuccess: Boolean
        get() = this == PlayerKickResult.SUCCESS
}
