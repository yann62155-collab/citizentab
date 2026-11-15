package fr.citizen.tab;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Fiches RP sauvegardées dans le monde (data/citizentab.dat) : survivent aux redémarrages. */
public class FicheData extends SavedData {

    /** Une fiche RP. age = 0 tant qu'il n'est pas renseigné. */
    public static final class Fiche {
        public String pseudo = "";
        public String prenom = "";
        public String nom = "";
        public int age = 0;
        public String profession = "";
        /** Une fois validée, seul un admin peut modifier la fiche. */
        public boolean validee = false;

        public boolean complete() {
            return !prenom.isEmpty() && !nom.isEmpty() && age > 0 && !profession.isEmpty();
        }
    }

    private static final SavedData.Factory<FicheData> FACTORY =
            new SavedData.Factory<>(FicheData::new, FicheData::load, null);

    private final Map<UUID, Fiche> fiches = new LinkedHashMap<>();

    public static FicheData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, CitizenTab.MOD_ID);
    }

    public Map<UUID, Fiche> all() { return fiches; }

    public Fiche get(UUID id) { return fiches.get(id); }

    public Fiche getOrCreate(UUID id, String pseudo) {
        Fiche f = fiches.computeIfAbsent(id, k -> new Fiche());
        if (pseudo != null && !pseudo.isEmpty()) f.pseudo = pseudo;
        setDirty();
        return f;
    }

    public boolean remove(UUID id) {
        boolean had = fiches.remove(id) != null;
        if (had) setDirty();
        return had;
    }

    private static FicheData load(CompoundTag tag, HolderLookup.Provider registries) {
        FicheData data = new FicheData();
        ListTag list = tag.getList("fiches", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            Fiche f = new Fiche();
            f.pseudo = c.getString("pseudo");
            f.prenom = c.getString("prenom");
            f.nom = c.getString("nom");
            f.age = c.getInt("age");
            f.profession = c.getString("profession");
            f.validee = c.getBoolean("validee");
            data.fiches.put(c.getUUID("uuid"), f);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        fiches.forEach((id, f) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("uuid", id);
            c.putString("pseudo", f.pseudo);
            c.putString("prenom", f.prenom);
            c.putString("nom", f.nom);
            c.putInt("age", f.age);
            c.putString("profession", f.profession);
            c.putBoolean("validee", f.validee);
            list.add(c);
        });
        tag.put("fiches", list);
        return tag;
    }
}
