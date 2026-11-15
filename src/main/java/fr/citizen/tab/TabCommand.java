package fr.citizen.tab;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Diagnostic pour les admins (OP 2) :
 *   /citizentab                 état de la liaison avec Citizen Economy + renvoie tout le Tab
 *   /citizentab voir <joueur>   ce que le recensement renvoie pour ce joueur
 */
public final class TabCommand {
    private TabCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("citizentab")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("voir").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "joueur"))))));
    }

    private static int status(CommandSourceStack src) {
        boolean ok = TabEvents.api() != null;
        src.sendSuccess(() -> Component.literal("[Citizen Tab] ").withStyle(ChatFormatting.GOLD)
                .append(ok ? Component.literal("Liaison avec le recensement de Citizen Economy : OK").withStyle(ChatFormatting.GREEN)
                        : Component.literal("Recensement de Citizen Economy introuvable : " + TabEvents.apiError()).withStyle(ChatFormatting.RED)), false);
        int n = TabEvents.refreshAll(src.getServer(), true);
        src.sendSuccess(() -> Component.literal("Liste Tab renvoyée pour " + n + " joueur(s).").withStyle(ChatFormatting.GRAY), false);
        return ok ? 1 : 0;
    }

    private static int show(CommandSourceStack src, ServerPlayer target) {
        String[] f = TabEvents.fiche(target.getUUID());
        String raw = f == null ? "aucune fiche (pas recensé, ou Citizen Economy pas encore à jour)" : String.join(" | ", f);
        src.sendSuccess(() -> Component.literal("[Citizen Tab] " + target.getGameProfile().getName() + " → ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(raw).withStyle(ChatFormatting.GRAY)), false);
        src.sendSuccess(() -> Component.literal("  Ligne du Tab : ").withStyle(ChatFormatting.GRAY).append(TabEvents.tabLine(target.getUUID())), false);
        target.refreshTabListName();
        return 1;
    }
}
