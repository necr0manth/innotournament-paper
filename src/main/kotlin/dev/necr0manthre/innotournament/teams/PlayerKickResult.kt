package dev.necr0manthre.innotournament.teams

enum class PlayerKickResult {
    SUCCESS,
    CANCELLED,
    NOT_IN_TEAM,
    TEAM_REMOVED;

    val isSuccess: Boolean
        get() = this == PlayerKickResult.SUCCESS
}
