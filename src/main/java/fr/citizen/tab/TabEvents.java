package fr.citizen.tab;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/** Remplace la ligne du joueur dans la liste Tab par son recensement (Citizen Economy). */
public final class TabEvents {
    private static final MethodHandle FICHE = find();

    private TabEvents() {}

    /** API de Citizen Economy : String[] Recensement.fiche(UUID) = { prénom, nom, âge, profession, validée "1"/"0" }. */
    private static MethodHandle find() {
        try {
            Class<?> api = Class.forName("fr.citizen.economy.api.Recensement");
            return MethodHandles.publicLookup().findStatic(api, "fiche", MethodType.methodType(String[].class, UUID.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            CitizenTab.LOGGER.warn("[Citizen Tab] Citizen Economy (recensement) introuvable : le Tab affichera « Citoyen non recensé ».");
            return null;
        }
    }

    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setDisplayName(tabLine(player.getUUID()));
        }
    }

    static String[] fiche(UUID id) {
        if (FICHE == null) return null;
        try {
            return (String[]) FICHE.invokeExact(id);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Exemple : « Aldric Valmont • 32 ans • Forgeron ». Jamais le pseudo Minecraft. */
    public static Component tabLine(UUID id) {
        String[] f = fiche(id);
        String first = f == null ? "" : f[0], last = f == null ? "" : f[1];
        if (first.isEmpty() && last.isEmpty()) {
            return Component.literal("Citoyen non recensé").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        int age = 0;
        try { age = Integer.parseInt(f[2]); } catch (RuntimeException ignored) { }
        MutableComponent line = Component.literal((first + " " + last).trim()).withStyle(ChatFormatting.GOLD);
        line.append(sep());
        line.append(Component.literal(age > 0 ? age + " ans" : "? ans").withStyle(ChatFormatting.WHITE));
        line.append(sep());
        line.append(Component.literal(f.length > 3 && !f[3].isEmpty() ? f[3] : "Sans emploi").withStyle(ChatFormatting.RED));
        return line;
    }

    private static Component sep() {
        return Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY);
    }
}
