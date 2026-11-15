package fr.citizen.tab;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Remplace la ligne du joueur dans la liste Tab par sa fiche RP. */
public final class TabEvents {
    private TabEvents() {}

    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setDisplayName(tabLine(player));
        }
    }

    /** Exemple : « Aldric Valmont • 32 ans • Forgeron ». Jamais le pseudo Minecraft. */
    public static Component tabLine(ServerPlayer player) {
        FicheData.Fiche f = fiche(player);
        if (f == null || (f.prenom.isEmpty() && f.nom.isEmpty())) {
            return Component.literal("Voyageur inconnu").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        MutableComponent line = Component.literal((f.prenom + " " + f.nom).trim()).withStyle(ChatFormatting.GOLD);
        line.append(sep());
        line.append(Component.literal(f.age > 0 ? f.age + " ans" : "? ans").withStyle(ChatFormatting.WHITE));
        line.append(sep());
        line.append(Component.literal(f.profession.isEmpty() ? "Sans profession" : f.profession)
                .withStyle(ChatFormatting.RED));
        return line;
    }

    private static Component sep() {
        return Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY);
    }

    static FicheData.Fiche fiche(ServerPlayer player) {
        if (player.server == null) return null;
        try {
            return FicheData.get(player.server).get(player.getUUID());
        } catch (Exception ex) {
            return null; // monde pas encore chargé
        }
    }

    /** À la connexion : met le pseudo à jour et rappelle de remplir la fiche si besoin. */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FicheData.Fiche f = fiche(player);
        if (f != null) {
            f.pseudo = player.getGameProfile().getName();
            FicheData.get(player.server).setDirty();
        }
        if (f == null || !f.validee) {
            player.sendSystemMessage(FicheCommand.prefix()
                    .append(Component.literal("Remplis ta fiche de citoyen : elle remplace ton pseudo dans la liste Tab.")
                            .withStyle(ChatFormatting.GRAY)));
            suggest(player, "/fiche prenom ", "Ton prénom");
            suggest(player, "/fiche nom ", "Ton nom de famille");
            suggest(player, "/fiche age ", "Ton âge");
            suggest(player, "/fiche profession ", "Ta profession");
            suggest(player, "/fiche valider", "Valider (la fiche ne sera plus modifiable)");
        }
    }

    private static void suggest(ServerPlayer player, String cmd, String what) {
        player.sendSystemMessage(Component.literal("  " + cmd.trim()).withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clique pour écrire la commande"))))
                .append(Component.literal(" — " + what).withStyle(ChatFormatting.GRAY)));
    }
}
