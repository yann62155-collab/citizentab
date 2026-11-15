package fr.citizen.tab;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.UUID;

/** Remplace la ligne du joueur dans la liste Tab par son recensement (Citizen Economy). */
public final class TabEvents {
    /** Vérification de la liste Tab : toutes les 2 secondes. */
    private static final int CHECK_TICKS = 40;
    private static volatile MethodHandle fiche;
    private static volatile String apiError = "pas encore cherchée";
    private static int ticks;

    private TabEvents() {}

    /**
     * API de Citizen Economy : String[] Recensement.fiche(UUID) = { prénom, nom, âge, profession, validée "1"/"0" }.
     * Cherchée quand tous les mods sont chargés, avec plusieurs chargeurs de classes.
     */
    static synchronized MethodHandle api() {
        if (fiche != null) return fiche;
        Throwable last = null;
        for (ClassLoader cl : new ClassLoader[]{Thread.currentThread().getContextClassLoader(), TabEvents.class.getClassLoader()}) {
            if (cl == null) continue;
            try {
                Class<?> api = Class.forName("fr.citizen.economy.api.Recensement", true, cl);
                MethodType type = MethodType.methodType(String[].class, UUID.class);
                MethodHandle mh;
                try { mh = MethodHandles.publicLookup().findStatic(api, "fiche", type); }
                catch (IllegalAccessException e) { mh = MethodHandles.lookup().findStatic(api, "fiche", type); }
                fiche = mh;
                apiError = null;
                return mh;
            } catch (Throwable t) {
                last = t;
            }
        }
        apiError = last == null ? "introuvable" : last.getClass().getSimpleName() + " : " + last.getMessage();
        return null;
    }

    static String apiError() { return apiError; }

    public static void onServerStarted(ServerStartedEvent event) {
        if (api() != null) {
            CitizenTab.LOGGER.info("[Citizen Tab] Recensement de Citizen Economy trouvé : la liste Tab affiche les fiches des citoyens.");
        } else {
            CitizenTab.LOGGER.error("[Citizen Tab] Recensement de Citizen Economy INTROUVABLE ({}) : installez Citizen Economy avec le recensement.", apiError);
        }
        refreshAll(event.getServer(), true);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) player.refreshTabListName();
    }

    /** Filet de sécurité : si la ligne affichée n'est plus celle du recensement, on la renvoie à tous les joueurs. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % CHECK_TICKS != 0) return;
        refreshAll(event.getServer(), false);
    }

    static int refreshAll(MinecraftServer server, boolean force) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (force || !Objects.equals(p.getTabListDisplayName(), tabLine(p.getUUID()))) {
                p.refreshTabListName();
                n++;
            }
        }
        return n;
    }

    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setDisplayName(tabLine(player.getUUID()));
        }
    }

    static String[] fiche(UUID id) {
        MethodHandle mh = api();
        if (mh == null) return null;
        try {
            return (String[]) mh.invokeExact(id);
        } catch (Throwable t) {
            apiError = t.getClass().getSimpleName() + " : " + t.getMessage();
            return null;
        }
    }

    /** Exemple : « Aldric Valmont • 32 ans • Forgeron ». Jamais le pseudo Minecraft. */
    public static Component tabLine(UUID id) {
        String[] f = fiche(id);
        String first = f == null || f.length < 2 ? "" : f[0], last = f == null || f.length < 2 ? "" : f[1];
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
