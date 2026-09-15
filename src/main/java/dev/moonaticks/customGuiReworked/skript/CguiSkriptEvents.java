package dev.moonaticks.customGuiReworked.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.util.SimpleEvent;
import ch.njol.skript.registrations.EventValues;
import ch.njol.skript.util.Getter;
import dev.moonaticks.customGuiReworked.api.event.GuiCloseEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiDragEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiOpenEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotChangedEvent;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import org.bukkit.entity.Player;

/**
 * События Skript для GUI: {@code on cgui open / cgui close / cgui click}.
 *
 * <p>Доступные event-values:
 * <ul>
 *   <li>{@code event-player} — игрок;</li>
 *   <li>{@code event-string} — имя GUI;</li>
 *   <li>{@code event-number} — слот (только для click).</li>
 * </ul>
 */
@SuppressWarnings("deprecation")
public final class CguiSkriptEvents {

    private CguiSkriptEvents() {
    }

    public static void register() {
        Skript.registerEvent("cgui open", SimpleEvent.class, GuiOpenEvent.class, "[cgui] open");
        Skript.registerEvent("cgui close", SimpleEvent.class, GuiCloseEvent.class, "[cgui] close");
        Skript.registerEvent("cgui click", SimpleEvent.class, GuiSlotClickEvent.class, "[cgui] click");
        Skript.registerEvent("cgui drag", SimpleEvent.class, GuiDragEvent.class, "[cgui] drag");
        Skript.registerEvent("cgui slot changed", SimpleEvent.class, GuiSlotChangedEvent.class, "[cgui] slot changed");

        EventValues.registerEventValue(GuiOpenEvent.class, Player.class, new Getter<Player, GuiOpenEvent>() {
            @Override
            public Player get(GuiOpenEvent event) {
                return event.getPlayer();
            }
        }, 0);
        EventValues.registerEventValue(GuiOpenEvent.class, String.class, new Getter<String, GuiOpenEvent>() {
            @Override
            public String get(GuiOpenEvent event) {
                return event.getGui() == null ? null : event.getGui().name();
            }
        }, 1);

        EventValues.registerEventValue(GuiCloseEvent.class, Player.class, new Getter<Player, GuiCloseEvent>() {
            @Override
            public Player get(GuiCloseEvent event) {
                return event.getPlayer();
            }
        }, 0);
        EventValues.registerEventValue(GuiCloseEvent.class, String.class, new Getter<String, GuiCloseEvent>() {
            @Override
            public String get(GuiCloseEvent event) {
                return event.getGui() == null ? null : event.getGui().name();
            }
        }, 1);

        EventValues.registerEventValue(GuiDragEvent.class, Player.class, new Getter<Player, GuiDragEvent>() {
            @Override
            public Player get(GuiDragEvent event) {
                return event.getPlayer();
            }
        }, 0);
        EventValues.registerEventValue(GuiDragEvent.class, String.class, new Getter<String, GuiDragEvent>() {
            @Override
            public String get(GuiDragEvent event) {
                return event.getGui() == null ? null : event.getGui().name();
            }
        }, 1);

        EventValues.registerEventValue(GuiSlotClickEvent.class, Player.class, new Getter<Player, GuiSlotClickEvent>() {
            @Override
            public Player get(GuiSlotClickEvent event) {
                return event.getPlayer();
            }
        }, 0);
        EventValues.registerEventValue(GuiSlotClickEvent.class, String.class, new Getter<String, GuiSlotClickEvent>() {
            @Override
            public String get(GuiSlotClickEvent event) {
                return event.getGui() == null ? null : event.getGui().name();
            }
        }, 1);
        EventValues.registerEventValue(GuiSlotClickEvent.class, Number.class, new Getter<Number, GuiSlotClickEvent>() {
            @Override
            public Number get(GuiSlotClickEvent event) {
                return event.getSlot();
            }
        }, 2);

        // cgui slot changed: игрок положил/забрал/перенёс предмет (или плагин
        // изменил слот серверно) — с предметами «было/стало».
        EventValues.registerEventValue(GuiSlotChangedEvent.class, Player.class, new Getter<Player, GuiSlotChangedEvent>() {
            @Override
            public Player get(GuiSlotChangedEvent event) {
                return event.getPlayer();
            }
        }, 0);
        EventValues.registerEventValue(GuiSlotChangedEvent.class, String.class, new Getter<String, GuiSlotChangedEvent>() {
            @Override
            public String get(GuiSlotChangedEvent event) {
                return event.getGui() == null ? null : event.getGui().name();
            }
        }, 1);
        EventValues.registerEventValue(GuiSlotChangedEvent.class, Number.class, new Getter<Number, GuiSlotChangedEvent>() {
            @Override
            public Number get(GuiSlotChangedEvent event) {
                return event.getSlot();
            }
        }, 2);
        EventValues.registerEventValue(GuiSlotChangedEvent.class, org.bukkit.Location.class,
                new Getter<org.bukkit.Location, GuiSlotChangedEvent>() {
                    @Override
                    public org.bukkit.Location get(GuiSlotChangedEvent event) {
                        org.bukkit.inventory.Inventory inventory = event.getInventory();
                        return inventory != null && inventory.getHolder() instanceof dev.moonaticks.customGuiReworked.gui.GuiHolder holder
                                && holder.key().type() == dev.moonaticks.customGuiReworked.api.StorageType.BLOCK
                                ? holder.blockLocation()
                                : null;
                    }
                }, 3);
        // Предметы «было/стало» — выражениями
        // «cgui old item of %event%» / «cgui new item of %event%»
        // (см. ExprCguiSlotItemChanged).
    }
}
