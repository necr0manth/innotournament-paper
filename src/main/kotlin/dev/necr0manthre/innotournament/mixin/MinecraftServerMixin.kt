package dev.necr0manthre.innotournament.mixin

import org.spongepowered.asm.mixin.Mixin

@Mixin(MinecraftServer::class)
class MinecraftServerMixin {
    @Inject(method = "reloadResources", at = At("TAIL"))
    private fun endResourceReload(
        collection: kotlin.collections.MutableCollection<kotlin.String?>?,
        cir: CallbackInfoReturnable<java.util.concurrent.CompletableFuture<java.lang.Void?>?>
    ) {
        cir.getReturnValue().handleAsync({ value, throwable ->
            EndServerReloadEvent.Companion.EVENT.invoker().stateChanged(this as kotlin.Any as MinecraftServer)
            value
        }, this as kotlin.Any as MinecraftServer)
    }
}
