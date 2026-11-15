package fr.citizen.tab;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Joueurs :
 *   /fiche                         voir sa fiche
 *   /fiche prenom|nom <texte>
 *   /fiche age <nombre>
 *   /fiche profession <texte>
 *   /fiche valider                 verrouille la fiche
 * Admins (OP niveau 2) :
 *   /fiche admin voir <joueur>
 *   /fiche admin prenom|nom|profession <joueur> <texte>
 *   /fiche admin age <joueur> <nombre>
 *   /fiche admin deverrouiller <joueur>
 *   /fiche admin supprimer <joueur>
 *   /fiche admin liste
 */
public final class FicheCommand {
    public static final int PERMISSION = 2;
    public static final int MAX_NAME = 20;
    public static final int MAX_PROFESSION = 24;
    public static final int MAX_AGE = 9999;
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N} '\\-.]+$");

    private enum Champ { PRENOM, NOM, PROFESSION }

    private FicheCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fiche")
                .executes(ctx -> showSelf(ctx.getSource()))
                .then(textField("prenom", Champ.PRENOM))
                .then(textField("nom", Champ.NOM))
                .then(textField("profession", Champ.PROFESSION))
                .then(Commands.literal("age")
                        .then(Commands.argument("age", IntegerArgumentType.integer(1, MAX_AGE))
                                .executes(ctx -> setAgeSelf(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "age")))))
                .then(Commands.literal("valider").executes(ctx -> validate(ctx.getSource())))
                .then(Commands.literal("aide").executes(ctx -> help(ctx.getSource())))
                .then(admin()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> textField(String literal, Champ champ) {
        return Commands.literal(literal)
                .then(Commands.argument("texte", StringArgumentType.greedyString())
                        .executes(ctx -> setTextSelf(ctx.getSource(), champ, StringArgumentType.getString(ctx, "texte"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("admin")
                .requires(src -> src.hasPermission(PERMISSION))
                .then(Commands.literal("liste").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("voir").then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                        .executes(ctx -> { GameProfile t = one(ctx); return t == null ? 0 : showOther(ctx.getSource(), t); })))
                .then(adminText("prenom", Champ.PRENOM))
                .then(adminText("nom", Champ.NOM))
                .then(adminText("profession", Champ.PROFESSION))
                .then(Commands.literal("age").then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                        .then(Commands.argument("age", IntegerArgumentType.integer(1, MAX_AGE))
                                .executes(ctx -> {
                                    GameProfile t = one(ctx);
                                    if (t == null) return 0;
                                    FicheData.Fiche f = FicheData.get(ctx.getSource().getServer()).getOrCreate(t.getId(), t.getName());
                                    f.age = IntegerArgumentType.getInteger(ctx, "age");
                                    return adminDone(ctx.getSource(), t, "âge → " + f.age + " ans");
                                }))))
                .then(Commands.literal("deverrouiller").then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                        .executes(ctx -> {
                            GameProfile t = one(ctx);
                            if (t == null) return 0;
                            FicheData.Fiche f = FicheData.get(ctx.getSource().getServer()).get(t.getId());
                            if (f == null) return fail(ctx.getSource(), t.getName() + " n'a pas de fiche.");
                            f.validee = false;
                            FicheData.get(ctx.getSource().getServer()).setDirty();
                            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(t.getId());
                            if (online != null) online.sendSystemMessage(prefix().append(Component.literal(
                                    "Un admin a déverrouillé ta fiche : tu peux la modifier puis refaire /fiche valider.").withStyle(ChatFormatting.GRAY)));
                            return adminDone(ctx.getSource(), t, "fiche déverrouillée");
                        })))
                .then(Commands.literal("supprimer").then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                        .executes(ctx -> {
                            GameProfile t = one(ctx);
                            if (t == null) return 0;
                            if (!FicheData.get(ctx.getSource().getServer()).remove(t.getId()))
                                return fail(ctx.getSource(), t.getName() + " n'a pas de fiche.");
                            return adminDone(ctx.getSource(), t, "fiche supprimée");
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminText(String literal, Champ champ) {
        return Commands.literal(literal).then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                .then(Commands.argument("texte", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            GameProfile t = one(ctx);
                            if (t == null) return 0;
                            String value = clean(StringArgumentType.getString(ctx, "texte"));
                            String error = check(champ, value);
                            if (error != null) return fail(ctx.getSource(), error);
                            FicheData.Fiche f = FicheData.get(ctx.getSource().getServer()).getOrCreate(t.getId(), t.getName());
                            apply(f, champ, value);
                            return adminDone(ctx.getSource(), t, label(champ) + " → " + value);
                        })));
    }

    // ------------------------------------------------------------------ joueur

    private static int setTextSelf(CommandSourceStack src, Champ champ, String raw) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        FicheData.Fiche f = FicheData.get(src.getServer()).getOrCreate(player.getUUID(), player.getGameProfile().getName());
        if (f.validee) return locked(src);
        String value = clean(raw);
        String error = check(champ, value);
        if (error != null) return fail(src, error);
        apply(f, champ, value);
        player.refreshTabListName();
        src.sendSuccess(() -> prefix().append(Component.literal(capital(label(champ)) + " : ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(value).withStyle(ChatFormatting.GOLD)), false);
        nextStep(src, f);
        return 1;
    }

    private static int setAgeSelf(CommandSourceStack src, int age) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        FicheData.Fiche f = FicheData.get(src.getServer()).getOrCreate(player.getUUID(), player.getGameProfile().getName());
        if (f.validee) return locked(src);
        f.age = age;
        FicheData.get(src.getServer()).setDirty();
        player.refreshTabListName();
        src.sendSuccess(() -> prefix().append(Component.literal("Âge : ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(age + " ans").withStyle(ChatFormatting.GOLD)), false);
        nextStep(src, f);
        return 1;
    }

    private static int validate(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        FicheData data = FicheData.get(src.getServer());
        FicheData.Fiche f = data.get(player.getUUID());
        if (f != null && f.validee) return fail(src, "Ta fiche est déjà validée.");
        if (f == null || !f.complete()) return fail(src, "Ta fiche n'est pas complète : il faut prénom, nom, âge et profession.");
        f.validee = true;
        data.setDirty();
        player.refreshTabListName();
        src.sendSuccess(() -> prefix().append(Component.literal("Fiche validée ! Voici ta ligne dans le Tab :").withStyle(ChatFormatting.GREEN)), false);
        src.sendSuccess(() -> Component.literal("  ").append(TabEvents.tabLine(player)), false);
        CitizenTab.LOGGER.info("[Citizen Tab] {} a validé sa fiche : {} {}, {} ans, {}",
                player.getGameProfile().getName(), f.prenom, f.nom, f.age, f.profession);
        return 1;
    }

    private static int showSelf(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        FicheData.Fiche f = FicheData.get(src.getServer()).get(player.getUUID());
        sendFiche(src, f, null);
        if (f == null || !f.validee) help(src);
        return 1;
    }

    private static void nextStep(CommandSourceStack src, FicheData.Fiche f) {
        String missing = f.prenom.isEmpty() ? "/fiche prenom <ton prénom>"
                : f.nom.isEmpty() ? "/fiche nom <ton nom>"
                : f.age <= 0 ? "/fiche age <ton âge>"
                : f.profession.isEmpty() ? "/fiche profession <ta profession>"
                : null;
        if (missing != null) {
            src.sendSuccess(() -> Component.literal("  Ensuite : " + missing).withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            src.sendSuccess(() -> Component.literal("  Fiche complète. Vérifie avec /fiche puis tape /fiche valider.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    // ------------------------------------------------------------------ admin

    private static int showOther(CommandSourceStack src, GameProfile target) {
        sendFiche(src, FicheData.get(src.getServer()).get(target.getId()), target.getName());
        return 1;
    }

    private static int list(CommandSourceStack src) {
        Map<UUID, FicheData.Fiche> all = FicheData.get(src.getServer()).all();
        if (all.isEmpty()) return fail(src, "Aucune fiche enregistrée.");
        src.sendSuccess(() -> prefix().append(Component.literal("Fiches (" + all.size() + ") :").withStyle(ChatFormatting.GRAY)), false);
        all.forEach((id, f) -> {
            boolean online = src.getServer().getPlayerList().getPlayer(id) != null;
            src.sendSuccess(() -> Component.literal(online ? " ● " : " ○ ").withStyle(online ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    .append(Component.literal(f.pseudo).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal((f.prenom + " " + f.nom).trim() + ", " + (f.age > 0 ? f.age + " ans" : "?")
                            + ", " + (f.profession.isEmpty() ? "?" : f.profession)).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(f.validee ? " ✔" : " (non validée)").withStyle(f.validee ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        });
        return all.size();
    }

    private static int adminDone(CommandSourceStack src, GameProfile target, String what) {
        MinecraftServer server = src.getServer();
        FicheData.get(server).setDirty();
        ServerPlayer online = server.getPlayerList().getPlayer(target.getId());
        if (online != null) online.refreshTabListName();
        CitizenTab.LOGGER.info("[Citizen Tab] {} a modifié la fiche de {} : {}", src.getTextName(), target.getName(), what);
        src.sendSuccess(() -> prefix().append(Component.literal(target.getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" : " + what).withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }

    private static GameProfile one(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "joueur");
        if (profiles.size() != 1) {
            fail(ctx.getSource(), "Indique un seul joueur.");
            return null;
        }
        return profiles.iterator().next();
    }

    // ------------------------------------------------------------------ outils

    private static void sendFiche(CommandSourceStack src, FicheData.Fiche f, String pseudo) {
        String title = pseudo == null ? "Ta fiche de citoyen" : "Fiche de " + pseudo;
        src.sendSuccess(() -> prefix().append(Component.literal(title
                + (f != null && f.validee ? " (validée)" : " (non validée)")).withStyle(ChatFormatting.GRAY)), false);
        row(src, "Prénom", f == null ? "" : f.prenom);
        row(src, "Nom", f == null ? "" : f.nom);
        row(src, "Âge", f == null || f.age <= 0 ? "" : f.age + " ans");
        row(src, "Profession", f == null ? "" : f.profession);
    }

    private static void row(CommandSourceStack src, String label, String value) {
        src.sendSuccess(() -> Component.literal("  " + label + " : ").withStyle(ChatFormatting.GRAY)
                .append(value.isEmpty()
                        ? Component.literal("à remplir").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC)
                        : Component.literal(value).withStyle(ChatFormatting.GOLD)), false);
    }

    private static int help(CommandSourceStack src) {
        line(src, "/fiche prenom <prénom>", "ton prénom");
        line(src, "/fiche nom <nom>", "ton nom de famille");
        line(src, "/fiche age <nombre>", "ton âge");
        line(src, "/fiche profession <métier>", "ta profession");
        line(src, "/fiche valider", "verrouille la fiche (un admin pourra la modifier)");
        if (src.hasPermission(PERMISSION)) {
            line(src, "/fiche admin liste | voir | prenom | nom | age | profession | deverrouiller | supprimer", "admins");
        }
        return 1;
    }

    private static void line(CommandSourceStack src, String cmd, String what) {
        src.sendSuccess(() -> Component.literal("  " + cmd).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" — " + what).withStyle(ChatFormatting.GRAY)), false);
    }

    private static void apply(FicheData.Fiche f, Champ champ, String value) {
        switch (champ) {
            case PRENOM -> f.prenom = value;
            case NOM -> f.nom = value;
            case PROFESSION -> f.profession = value;
        }
    }

    private static String check(Champ champ, String value) {
        int max = champ == Champ.PROFESSION ? MAX_PROFESSION : MAX_NAME;
        if (value.isEmpty()) return "Le texte ne peut pas être vide.";
        if (value.length() > max) return capital(label(champ)) + " trop long (" + max + " caractères maximum).";
        if (!ALLOWED.matcher(value).matches()) return "Caractères autorisés : lettres, chiffres, espaces, ' - .";
        if (value.chars().noneMatch(Character::isLetter)) return "Il faut au moins une lettre.";
        return null;
    }

    private static String label(Champ champ) {
        return switch (champ) {
            case PRENOM -> "prénom";
            case NOM -> "nom";
            case PROFESSION -> "profession";
        };
    }

    private static String clean(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int locked(CommandSourceStack src) {
        return fail(src, "Ta fiche est validée : demande à un admin pour la modifier.");
    }

    static MutableComponent prefix() {
        return Component.literal("[Citoyen] ").withStyle(ChatFormatting.GOLD);
    }

    private static int fail(CommandSourceStack src, String message) {
        src.sendFailure(prefix().append(Component.literal(message).withStyle(ChatFormatting.RED)));
        return 0;
    }
}
