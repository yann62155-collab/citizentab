package fr.citizen.tab;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Citizen Tab : la liste Tab n'affiche plus les pseudos des joueurs,
 * seulement les informations RP saisies sur le serveur :
 *   Prénom Nom • âge • profession
 * Tout se passe côté serveur : rien à installer chez les joueurs.
 */
@Mod(CitizenTab.MOD_ID)
public class CitizenTab {
    public static final String MOD_ID = "citizentab";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CitizenTab(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(FicheCommand::register);
        // LOWEST : passe après les autres mods (ex. Citizen Noms) pour que la fiche RP gagne dans le Tab.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TabEvents::onTabListNameFormat);
        NeoForge.EVENT_BUS.addListener(TabEvents::onLogin);
    }
}
