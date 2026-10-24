package za.co.afritiers.tagger.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import za.co.afritiers.tagger.client.gui.TaggerSettingsScreen;

public final class ModMenuIntegration implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TaggerSettingsScreen::new;
    }
}
