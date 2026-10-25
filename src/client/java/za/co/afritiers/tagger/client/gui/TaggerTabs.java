package za.co.afritiers.tagger.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import java.util.function.Consumer;

/** Shared navigation keeps all three configuration tabs aligned at every GUI scale. */
final class TaggerTabs {
    private TaggerTabs() { }
    static void add(Consumer<Button> add, TaggerSettingsScreen owner, String active, int x, int y, int width) {
        String[] labels = {"Settings", "Profiles", "Waitlists"};
        for (int index = 0; index < labels.length; index++) {
            String label = labels[index];
            var tab = new TaggerWidgets.Tab(label, label.equals(active), pressed -> {
                if (label.equals(active)) return;
                Minecraft client = Minecraft.getInstance();
                client.gui.setScreen(switch (label) {
                    case "Profiles" -> new ProfilesScreen(owner);
                    case "Waitlists" -> new WaitlistsScreen(owner);
                    default -> owner;
                });
            });
            tab.setPosition(x + width - 218 + index * 70, y + 9);
            add.accept(tab);
        }
    }
}
