package dev.moonaticks.customGuiReworked.api;

import org.bukkit.inventory.ItemStack;

/**
 * Флюентный конструктор GUI.
 *
 * <pre>{@code
 * Gui gui = GuiBuilder.named("shop")
 *         .title("§6Shop")
 *         .size(27)
 *         .slot(10, SlotType.CONTAINER)
 *         .slot(11, SlotType.CONTAINER)
 *         .slot(20, SlotType.CONTAINER)
 *         .design(0, new ItemStack(Material.GRAY_STAINED_GLASS_PANE))
 *         .command(20, "give %player% diamond 1", 0)
 *         .storage(StorageType.PERSONAL)
 *         .blockId("my_custom_block")
 *         .build();
 * CustomGuiAPI.saveGui(gui);
 * CustomGuiAPI.openGui(player, "shop");
 * }</pre>
 *
 * <p>Важно: {@link #size(int)} должен вызываться до {@link #slot(int, SlotType)},
 * {@link #design(int, ItemStack)} и {@link #command(int, String, int)} —
 * иными словами, сначала задайте размер.
 */
public class GuiBuilder {

    private final Gui gui;

    private GuiBuilder(String name) {
        this.gui = new Gui(name);
    }

    /** Начинает построение GUI с указанным именем. */
    public static GuiBuilder named(String name) {
        return new GuiBuilder(name);
    }

    public GuiBuilder title(String title) {
        gui.title(title);
        return this;
    }

    /** Задаёт размер (9/18/27/36/45/54; прочее число округляется до ближайшего ряда). */
    public GuiBuilder size(int slots) {
        gui.slots(slots);
        return this;
    }

    public GuiBuilder storage(StorageType storage) {
        gui.storage(storage);
        return this;
    }

    /** Категория для меню /gui (по умолчанию none). */
    public GuiBuilder category(String id) {
        gui.category(id);
        return this;
    }

    public GuiBuilder category(GuiCategory category) {
        gui.category(category);
        return this;
    }

    /** Задаёт тип одного слота. */
    public GuiBuilder slot(int slot, SlotType type) {
        gui.setSlotType(slot, type);
        return this;
    }

    /** Задаёт тип нескольких слотов. */
    public GuiBuilder slots(java.util.List<Integer> slots, SlotType type) {
        for (Integer slot : slots) {
            if (slot != null) {
                gui.setSlotType(slot, type);
            }
        }
        return this;
    }

    /** Ставит предмет в дизайн-слот (предмет кодируется сразу). */
    public GuiBuilder design(int slot, ItemStack item) {
        gui.setDesignItem(slot, item);
        return this;
    }

    /** Ставит готовый payload в дизайн-слот (формат см. {@link dev.moonaticks.customGuiReworked.codec.Codecs}). */
    public GuiBuilder design(int slot, String payload) {
        gui.setDesignAt(slot, payload);
        return this;
    }

    /** Привязывает команду к слоту. */
    public GuiBuilder command(int slot, String command, int delayTicks) {
        gui.addCommand(new SlotCommand(slot, command, delayTicks));
        return this;
    }

    /** Привязывает ID кастомного блока (ItemsAdder/CraftEngine) к этому GUI. */
    public GuiBuilder blockId(String blockId) {
        gui.addBlockId(blockId);
        return this;
    }

    /**
     * Завершает построение.
     *
     * @throws IllegalStateException если скелет пуст (GUI невалиден)
     */
    public Gui build() {
        if (gui.skeleton().isEmpty()) {
            throw new IllegalStateException("GUI " + gui.name() + " has an empty skeleton");
        }
        return gui;
    }
}
