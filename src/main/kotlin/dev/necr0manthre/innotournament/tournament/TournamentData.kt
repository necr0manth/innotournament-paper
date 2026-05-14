package dev.necr0manthre.innotournament.tournament

import lombok.AllArgsConstructor

@AllArgsConstructor
class TournamentData private constructor() : Cloneable {
    var tournamentSpawn: BlockPos = BlockPos.ZERO
    var tournamentSpawnDimension: ResourceKey<net.minecraft.world.level.Level?> =
        net.minecraft.world.level.Level.OVERWORLD
    var tournamentPreSpawn: BlockPos = BlockPos.ZERO
    var tournamentPreSpawnDimension: ResourceKey<net.minecraft.world.level.Level?> =
        net.minecraft.world.level.Level.OVERWORLD
    var startBoxStructureResourceLocation: ResourceLocation? = ResourceLocation.fromNamespaceAndPath("", "")
    var startBoxPos: BlockPos = BlockPos.ZERO
    var preStartGameMode: GameType = GameType.SURVIVAL
    var preEventsPath: java.nio.file.Path = java.nio.file.Path.of("")
    var mainEventsPath: java.nio.file.Path = java.nio.file.Path.of("")
    var teamScoreModifiers: kotlin.DoubleArray? = kotlin.doubleArrayOf(2.43, 1.45, 1.0, 0.7241170, 0.4530344, 0.3645)
    var lastTeamScore: kotlin.Float = 0f

    fun createTournament(tournamentDataPath: java.nio.file.Path): Tournament {
        return Tournament(
            tournamentSpawn,
            tournamentSpawnDimension,
            tournamentPreSpawn,
            tournamentPreSpawnDimension,
            startBoxStructureResourceLocation,
            startBoxPos,
            preStartGameMode,
            EventLoader.loadEvents(tournamentDataPath.getParent().resolve(preEventsPath)),
            EventLoader.loadEvents(tournamentDataPath.getParent().resolve(mainEventsPath)),
            teamScoreModifiers,
            lastTeamScore
        )
    }

    //	public void applyToTournament(Tournament tournament, Path tournamentDataPath){
    //		if(tournament.phase!=0)
    //			throw new IllegalStateException("Tournament is already started");
    //		tournament.tournamentSpawn = tournamentSpawn;
    //		tournament.tournamentSpawnDimension = tournamentSpawnDimension;
    //		tournament.tournamentPreSpawn = tournamentPreSpawn;
    //		tournament.tournamentPreSpawnDimension = tournamentPreSpawnDimension;
    //		tournament.startBoxStructureResourceLocation = startBoxStructureResourceLocation;
    //		tournament.startBoxPos = startBoxPos;
    //		tournament.preStartEvents = EventLoader.loadEvents(tournamentDataPath.getParent().resolve(preEventsPath));
    //		tournament.mainEvents = EventLoader.loadEvents(tournamentDataPath.getParent().resolve(mainEventsPath));
    //	}
    public override fun clone(): TournamentData {
        return TournamentData(
            tournamentSpawn,
            tournamentSpawnDimension,
            tournamentPreSpawn,
            tournamentPreSpawnDimension,
            startBoxStructureResourceLocation,
            startBoxPos,
            preStartGameMode,
            preEventsPath,
            mainEventsPath,
            teamScoreModifiers!!.clone(),
            lastTeamScore
        )
    }

    companion object {
        val CODEC: com.mojang.serialization.Codec<TournamentData?>? = object : MapCodec<TournamentData?>() {
            private val errors: java.util.Queue<kotlin.Throwable?> = java.util.ArrayDeque<kotlin.Throwable?>()

            private fun <T> getAndCollectErrors(supplier: java.util.function.Supplier<T?>): java.util.Optional<T?> {
                try {
                    return java.util.Optional.ofNullable<T?>(supplier.get())
                } catch (e: kotlin.Throwable) {
                    errors.add(e)
                    return java.util.Optional.empty<T?>()
                }
            }

            private fun <T, V> getAndCollectErrors(
                codec: com.mojang.serialization.Codec<T?>,
                ops: DynamicOps<V?>?,
                map: MapLike<V?>,
                key: kotlin.String?
            ): java.util.Optional<T?> {
                return getAndCollectErrors<T?>(java.util.function.Supplier {
                    codec.decode<V?>(ops, map.get(key)).getOrThrow().getFirst()
                })
            }

            @kotlin.Throws(kotlin.Throwable::class)
            private fun throwAll() {
                if (errors.isEmpty()) return
                val errors: kotlin.Array<kotlin.Throwable?> =
                    this.errors.toArray<kotlin.Throwable?>(IntFunction { _Dummy_.__Array__() })
                this.errors.clear()
                if (errors.size == 1) throw errors[0]
                throw java.lang.RuntimeException(
                    "Multiple errors occurred: " + java.util.Arrays.stream<kotlin.Throwable?>(
                        errors
                    ).map<kotlin.String?> { obj: kotlin.Throwable? -> obj!!.message }
                        .collect(java.util.stream.Collectors.joining("\n")))
            }

            override fun <T> keys(ops: DynamicOps<T?>): java.util.stream.Stream<T?>? {
                return java.util.stream.Stream.of<kotlin.String?>(
                    "tournamentPreSpawn",
                    "tournamentPreSpawnDimension",
                    "tournamentSpawn",
                    "tournamentSpawnDimension",
                    "startBoxStructureResourceLocation",
                    "startBoxPos",
                    "preStartGameMode",
                    "preEventsPath",
                    "mainEventsPath",
                    "teamScoreModifiers",
                    "lastTeamScore"
                )
                    .map<T?> { value: kotlin.String? -> ops.createString(value) }
            }

            override fun <T> decode(ops: DynamicOps<T?>, input: MapLike<T?>): DataResult<TournamentData?> {
                val tournamentData = TournamentData()
                try {
                    tournamentData.tournamentPreSpawn =
                        getAndCollectErrors<BlockPos, T?>(BlockPos.CODEC, ops, input, "tournamentPreSpawn").orElse(
                            BlockPos.ZERO
                        )
                    tournamentData.tournamentPreSpawnDimension = getAndCollectErrors<kotlin.Any?, T?>(
                        ResourceLocation.CODEC,
                        ops,
                        input,
                        "tournamentPreSpawnDimension"
                    ).map<kotlin.Any>(java.util.function.Function { rl: kotlin.Any? ->
                        ResourceKey.create<T?>(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            rl
                        )
                    }).orElse(net.minecraft.world.level.Level.OVERWORLD)
                    tournamentData.tournamentSpawn = getAndCollectErrors<BlockPos, T?>(
                        BlockPos.CODEC,
                        ops,
                        input,
                        "tournamentSpawn"
                    ).orElse(BlockPos.ZERO)
                    tournamentData.tournamentSpawnDimension = getAndCollectErrors<kotlin.Any?, T?>(
                        ResourceLocation.CODEC,
                        ops,
                        input,
                        "tournamentSpawnDimension"
                    ).map<kotlin.Any>(java.util.function.Function { rl: kotlin.Any? ->
                        ResourceKey.create<T?>(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            rl
                        )
                    }).orElse(net.minecraft.world.level.Level.OVERWORLD)
                    tournamentData.startBoxStructureResourceLocation = getAndCollectErrors<kotlin.Any?, T?>(
                        ResourceLocation.CODEC,
                        ops,
                        input,
                        "startBoxStructureResourceLocation"
                    ).orElse(null)
                    tournamentData.startBoxPos = getAndCollectErrors<BlockPos, T?>(
                        BlockPos.CODEC,
                        ops,
                        input,
                        "startBoxPos"
                    ).orElse(BlockPos.ZERO)
                    tournamentData.preStartGameMode =
                        getAndCollectErrors<GameType, T?>(GameType.CODEC, ops, input, "preStartGameMode").orElse(
                            GameType.SURVIVAL
                        )
                    tournamentData.preEventsPath = getAndCollectErrors<kotlin.String?, T?>(
                        com.mojang.serialization.Codec.STRING,
                        ops,
                        input,
                        "preEventsPath"
                    ).map<java.nio.file.Path>(java.util.function.Function { first: kotlin.String? ->
                        java.nio.file.Path.of(
                            first
                        )
                    }).orElse(java.nio.file.Path.of("/"))
                    tournamentData.mainEventsPath = getAndCollectErrors<kotlin.String?, T?>(
                        com.mojang.serialization.Codec.STRING,
                        ops,
                        input,
                        "mainEventsPath"
                    ).map<java.nio.file.Path>(java.util.function.Function { first: kotlin.String? ->
                        java.nio.file.Path.of(
                            first
                        )
                    }).orElse(java.nio.file.Path.of("/"))
                    tournamentData.teamScoreModifiers =
                        getAndCollectErrors<kotlin.DoubleArray?>(java.util.function.Supplier {
                            ops.getStream(input.get("teamScoreModifiers")).getOrThrow()
                                .mapToDouble { v: T? -> ops.getNumberValue(v).getOrThrow().toDouble() }.toArray()
                        }).orElse(tournamentData.teamScoreModifiers)
                    tournamentData.lastTeamScore = getAndCollectErrors<kotlin.Float?, T?>(
                        com.mojang.serialization.Codec.FLOAT,
                        ops,
                        input,
                        "lastTeamScore"
                    ).orElse(0f)!!
                    throwAll()
                    return DataResult.success<TournamentData?>(tournamentData)
                } catch (e: kotlin.Throwable) {
                    return DataResult.error<TournamentData?>(java.util.function.Supplier { e.message }, tournamentData)
                }
            }

            override fun <T> encode(
                input: TournamentData,
                ops: DynamicOps<T?>,
                prefix: RecordBuilder<T?>
            ): RecordBuilder<T?> {
                prefix.add<BlockPos?>("tournamentPreSpawn", input.tournamentPreSpawn, BlockPos.CODEC)
                prefix.add<E?>(
                    "tournamentPreSpawnDimension",
                    input.tournamentPreSpawnDimension.location(),
                    ResourceLocation.CODEC
                )
                prefix.add<BlockPos?>("tournamentSpawn", input.tournamentSpawn, BlockPos.CODEC)
                prefix.add<E?>(
                    "tournamentSpawnDimension",
                    input.tournamentSpawnDimension.location(),
                    ResourceLocation.CODEC
                )
                prefix.add<kotlin.Any?>(
                    "startBoxStructureResourceLocation",
                    input.startBoxStructureResourceLocation,
                    ResourceLocation.CODEC
                )
                prefix.add<BlockPos?>("startBoxPos", input.startBoxPos, BlockPos.CODEC)
                prefix.add<GameType?>("preStartGameMode", input.preStartGameMode, GameType.CODEC)
                prefix.add<kotlin.String?>(
                    "preEventsPath",
                    input.preEventsPath.toString(),
                    com.mojang.serialization.Codec.STRING
                )
                prefix.add<kotlin.String?>(
                    "mainEventsPath",
                    input.mainEventsPath.toString(),
                    com.mojang.serialization.Codec.STRING
                )
                prefix.add(
                    "teamScoreModifiers",
                    ops.createList(
                        java.util.Arrays.stream(input.teamScoreModifiers)
                            .mapToObj<T?>(DoubleFunction { value: kotlin.Double -> ops.createDouble(value) })
                    )
                )
                prefix.add("lastTeamScore", ops.createFloat(input.lastTeamScore))
                return prefix
            }
        }.codec()
    }
}
