package world.bentobox.gg;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;



public class GgPladdon extends Pladdon
{
    Addon addon;
    @Override
    public Addon getAddon()
    {
        if (addon == null) {
            addon = new GgAddon();
        }
        return addon;
    }
}
