package dev.necr0manthre.innotournament.teams

class PlayerJoinResult private constructor(private val kickResult: PlayerKickResult?) {
    val isSuccess: Boolean
        get() = this === SUCCESS

    companion object {
        @JvmField
        val SUCCESS: PlayerJoinResult = PlayerJoinResult(null)
        @JvmField
        val ALREADY_IN_TEAM: PlayerJoinResult = PlayerJoinResult(null)
        @JvmField
        val CANCELLED: PlayerJoinResult = PlayerJoinResult(null)
        @JvmStatic
        fun cannotKick(kickResult: PlayerKickResult?): PlayerJoinResult {
            return PlayerJoinResult(kickResult)
        }
    }
}
