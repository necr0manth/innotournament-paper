package dev.necr0manthre.innotournament.commands;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** The command subtree and its permission gate can be checked without a running Paper server. */
class InnoGiveCommandTest {
    @Test
    void exposesOnlyGiveWithOptionalCountAndRequiresAdminPermission() {
        var give = InnoGiveCommand.INSTANCE.build().build();
        assertEquals("give", give.getLiteral());
        assertNotNull(give.getCommand(), "missing arguments should produce usage");

        var player = give.getChild("player");
        assertInstanceOf(ArgumentCommandNode.class, player);
        assertNotNull(player.getCommand(), "missing item should produce usage");
        var item = player.getChild("item");
        assertInstanceOf(ArgumentCommandNode.class, item);
        assertNotNull(item.getCommand(), "omitted count should default to one");
        var count = item.getChild("count");
        assertInstanceOf(ArgumentCommandNode.class, count);
        assertNotNull(count.getCommand(), "explicit count should be executable");

        assertFalse(give.getRequirement().test(source(false)));
        assertTrue(give.getRequirement().test(source(true)));
    }

    private static CommandSourceStack source(boolean allowed) {
        var sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) {
                        assertEquals("minecraft.command.function", args[0]);
                        return allowed;
                    }
                    throw new AssertionError("Unexpected sender method: " + method.getName());
                });
        return (CommandSourceStack) Proxy.newProxyInstance(CommandSourceStack.class.getClassLoader(),
                new Class<?>[]{CommandSourceStack.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getSender")) return sender;
                    throw new AssertionError("Unexpected source method: " + method.getName());
                });
    }
}
