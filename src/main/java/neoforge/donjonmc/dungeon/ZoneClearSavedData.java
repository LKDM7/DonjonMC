package neoforge.donjonmc.dungeon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistance de l'état des zones de donjon (file de nettoyage, IDs libres,
 * compteur d'IDs). Stocké dans l'overworld (data/donjonmc_zones.dat) ; sans
 * cela, un redémarrage pendant les 3 minutes avant effacement perdait la file
 * et remettait le compteur à 0, donc le donjon suivant pouvait se générer
 * par-dessus une zone sale.
 *
 * Le contenu vit dans {@link DungeonManager} : ce SavedData ne fait que
 * sérialiser/désérialiser son état et porter le flag dirty.
 */
public class ZoneClearSavedData extends SavedData {

    private static final String NAME = "donjonmc_zones";

    private static final SavedData.Factory<ZoneClearSavedData> FACTORY =
        new SavedData.Factory<>(ZoneClearSavedData::new, ZoneClearSavedData::load, null);

    /** Charge (ou crée) le SavedData et peuple DungeonManager au passage. */
    public static ZoneClearSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private ZoneClearSavedData() {}

    private static ZoneClearSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonManager.getInstance().loadZoneState(tag);
        return new ZoneClearSavedData();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return DungeonManager.getInstance().saveZoneState(tag);
    }
}
