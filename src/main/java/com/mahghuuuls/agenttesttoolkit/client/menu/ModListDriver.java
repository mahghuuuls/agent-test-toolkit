package com.mahghuuuls.agenttesttoolkit.client.menu;

import com.mahghuuuls.agenttesttoolkit.client.ScreenshotTaker;
import com.mahghuuuls.agenttesttoolkit.client.screen.ScreenReader;
import com.mahghuuuls.agenttesttoolkit.net.ScreenshotRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows one mod's entry in the Mods list from the title screen, pictures it when asked, and goes
 * back to the title screen.
 *
 * <p>It opens the list as the title screen's Mods button does, then works with whichever list
 * really opened: Forge's, or Cleanroom's own, which Cleanroom puts in Forge's place as it opens.
 * Neither offers a way to select by mod id, so each is reached through its own fields and calls,
 * found by name, Cleanroom's without any compile dependency. Forge's: its list of mods, its own
 * select call, and its list's scroll distance so the entry is in view. Cleanroom's: its list
 * widget, the entry whose data has the mod id, the same select calls a left click on the row
 * makes (never the click itself, whose pin button would change the favourites), and the list's
 * own call that centres an entry. The filters and favourites are never written. Two frames after
 * the selection, so the details are drawn, it reads the name and version shown and takes the
 * picture. Client thread only.
 */
final class ModListDriver {

    /** Where the outcome goes: done, failed with a reason, and the fields of the answer. */
    interface Done {
        void finished(String outcome, String reason, Map<String, String> fields);
    }

    static final String FORGE_LIST = "net.minecraftforge.fml.client.GuiModList";
    static final String CLEANROOM_LIST = "com.cleanroommc.client.modlist.screen.ModListScreen";

    /** The mod id is not loaded in this game. */
    static final String REASON_NOT_FOUND = "notFound";
    /** {@code mods} is taken only at the title screen. */
    static final String REASON_NOT_TITLE_SCREEN = "notTitleScreen";
    /** The list that opened is not one the toolkit can drive, or its fields were not as expected. */
    static final String REASON_UNSUPPORTED_LIST = "unsupportedList";
    /** The list does not show the mod's entry, such as one hidden by Cleanroom's kept filters. */
    static final String REASON_HIDDEN = "hidden";
    /** The list was closed, or replaced, before the entry was pictured. */
    static final String REASON_LIST_CLOSED = "listClosed";

    /** Frames drawn after the selection before the details are read and pictured. */
    static final int SETTLE_FRAMES = 2;

    private enum Stage { OPENED, SELECTED, PICTURING, DONE }

    private final MenuRequest request;
    private final ScreenshotTaker screenshots;
    private final Done done;
    private final Map<String, String> fields = new LinkedHashMap<String, String>();
    private GuiScreen title;
    private GuiScreen list;
    private boolean cleanroom;
    private Stage stage = Stage.OPENED;
    private int frames;
    /** A picture's outcome, told on the client thread at a render tick's end. */
    private boolean pictureTaken;
    private boolean pictureSaved;
    private String pictureReason;

    ModListDriver(MenuRequest request, ScreenshotTaker screenshots, Done done) {
        this.request = request;
        this.screenshots = screenshots;
        this.done = done;
    }

    /** Whether a mod of exactly this id is loaded; checked before anything opens. */
    static boolean isLoaded(String modId) {
        return Loader.instance().getIndexedModList().containsKey(modId);
    }

    /** Opens the list as the Mods button does; the actual screen is the one open after it. */
    void open(Minecraft mc) {
        title = mc.currentScreen;
        String modId = request.getArgs().get(0);
        fields.put("modId", modId);
        mc.displayGuiScreen(new GuiModList(title));
        list = mc.currentScreen;
        String name = list == null ? "none" : list.getClass().getName();
        fields.put("screen", name);
        List<String> hierarchy = list == null ? new ArrayList<String>() : ScreenReader.hierarchy(list.getClass());
        if (hierarchy.contains(CLEANROOM_LIST)) {
            cleanroom = true;
        } else if (!(list instanceof GuiModList)) {
            fields.put("detail", name);
            finish(mc, "failed", REASON_UNSUPPORTED_LIST);
        }
    }

    /** One client tick end: the selection on the first, once the list has settled; the return at the end. */
    void tick(Minecraft mc) {
        if (stage == Stage.DONE) {
            return;
        }
        if (mc.currentScreen != list) {
            finish(mc, "failed", REASON_LIST_CLOSED);
            return;
        }
        if (stage == Stage.OPENED) {
            select(mc);
        } else if (stage == Stage.PICTURING && pictureTaken) {
            if (!pictureSaved) {
                finish(mc, "failed", pictureReason == null ? "noFile" : pictureReason);
            } else {
                finish(mc, "done", null);
            }
        }
    }

    /** One frame drawn: the details settle, then the name and version are read and the picture asked. */
    void frame(Minecraft mc) {
        if (stage != Stage.SELECTED || ++frames < SETTLE_FRAMES) {
            return;
        }
        if (request.getScreenshot().isEmpty()) {
            finish(mc, "done", null);
            return;
        }
        stage = Stage.PICTURING;
        ScreenshotRequest picture = new ScreenshotRequest(request.getScreenshot(), 0, System.currentTimeMillis(),
                request.getHide());
        screenshots.takeLocal(picture, new ScreenshotTaker.Taken() {
            @Override
            public void taken(boolean saved, String file, String reason) {
                // The taker's own name for the file, absent when it tried none, such as a full queue.
                if (file != null && stage != Stage.DONE) {
                    fields.put("file", file);
                }
                pictureTaken = true;
                pictureSaved = saved;
                pictureReason = reason;
            }
        });
    }

    boolean isDone() {
        return stage == Stage.DONE;
    }

    /**
     * Ends the step now as failed with this reason, such as the channel's deadline: back to the
     * title screen when the list is still open, then the answer, as any other ending.
     */
    void end(Minecraft mc, String reason) {
        finish(mc, "failed", reason);
    }

    private void select(Minecraft mc) {
        String modId = request.getArgs().get(0);
        try {
            String hidden = cleanroom ? selectCleanroom(modId) : selectForge(modId);
            if (hidden != null) {
                fields.put("filter", hidden);
                finish(mc, "failed", REASON_HIDDEN);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fields.put("detail", failure.getClass().getSimpleName());
            finish(mc, "failed", REASON_UNSUPPORTED_LIST);
            return;
        }
        stage = Stage.SELECTED;
        frames = 0;
    }

    /** Forge's list: its own select call, then its scroll distance set so the entry shows. Null when shown. */
    private String selectForge(String modId) throws ReflectiveOperationException {
        GuiModList screen = (GuiModList) list;
        List<?> mods = (List<?>) field(GuiModList.class, "mods").get(screen);
        List<String> ids = new ArrayList<String>();
        for (Object mod : mods) {
            ids.add(mod instanceof ModContainer ? ((ModContainer) mod).getModId() : "");
        }
        int index = ModListChoice.indexOf(ids, modId);
        if (index < 0) {
            ModContainer loaded = Loader.instance().getIndexedModList().get(modId);
            // Forge lists a child mod under its parent, not on its own.
            return loaded != null && loaded.getMetadata() != null && loaded.getMetadata().parentMod != null
                    ? "childOf:" + loaded.getMetadata().parentMod.getModId() : "notListed";
        }
        screen.selectModIndex(index);
        Object slots = field(GuiModList.class, "modList").get(screen);
        Class<?> scrolling = net.minecraftforge.fml.client.GuiScrollingList.class;
        int top = field(scrolling, "top").getInt(slots);
        int bottom = field(scrolling, "bottom").getInt(slots);
        int slotHeight = field(scrolling, "slotHeight").getInt(slots);
        field(scrolling, "scrollDistance").setFloat(slots,
                ModListChoice.scrollFor(index, slotHeight, bottom - top, mods.size() * slotHeight + 1));
        ModContainer mod = (ModContainer) mods.get(index);
        fields.put("name", mod.getName());
        fields.put("version", mod.getDisplayVersion());
        return null;
    }

    /** Cleanroom's list: the entry by its data's mod id, selected and centred by the list's own calls. Null when shown. */
    private String selectCleanroom(String modId) throws ReflectiveOperationException {
        Object widget = field(list.getClass(), "modList").get(list);
        List<?> entries = (List<?>) method(widget.getClass(), "children", 0).invoke(widget);
        Object entry = null;
        Object data = null;
        for (Object candidate : entries) {
            Object candidateData = method(candidate.getClass(), "getData", 0).invoke(candidate);
            Object id = candidateData == null ? null : method(candidateData.getClass(), "getModId", 0).invoke(candidateData);
            if (modId.equals(id)) {
                entry = candidate;
                data = candidateData;
                break;
            }
        }
        if (entry == null) {
            return cleanroomFilters();
        }
        // What a left click on the row does, without the pin button the same click could hit.
        method(list.getClass(), "setSelectedModData", 1).invoke(list, data);
        method(widget.getClass(), "setSelected", 1).invoke(widget, entry);
        method(widget.getClass(), "centerScrollOn", 1).invoke(widget, entry);
        Object name = method(data.getClass(), "getDisplayName", 0).invoke(data);
        Object version = method(data.getClass(), "getVersion", 0).invoke(data);
        fields.put("name", name == null ? "" : name.toString());
        fields.put("version", version == null ? "" : version.toString());
        return null;
    }

    /** The filters Cleanroom's list keeps between openings, as far as they can be read. */
    private String cleanroomFilters() {
        String query = null;
        Map<String, Boolean> options = new LinkedHashMap<String, Boolean>();
        try {
            Object value = field(list.getClass(), "optionQuery").get(null);
            query = value == null ? null : value.toString();
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            query = null;
        }
        String[][] names = {{"OPTION_HIDE_LIBRARIES", "hideLibraries"}, {"OPTION_HIDE_CHILD_MODS", "hideChildMods"},
                {"OPTION_CONFIGS_ONLY", "configsOnly"}, {"OPTION_UPDATES_ONLY", "updatesOnly"},
                {"OPTION_FAVOURITES_ONLY", "favouritesOnly"}};
        for (String[] name : names) {
            try {
                Object flag = field(list.getClass(), name[0]).get(null);
                Object on = flag == null ? null : method(flag.getClass(), "booleanValue", 0).invoke(flag);
                options.put(name[1], Boolean.TRUE.equals(on));
            } catch (ReflectiveOperationException | RuntimeException unreadable) {
                // Not readable in this version; left out of the answer.
            }
        }
        return ModListChoice.filters(query, options);
    }

    /** Back to the title screen the list was opened from, and the answer. */
    private void finish(Minecraft mc, String outcome, String reason) {
        if (stage == Stage.DONE) {
            return;
        }
        stage = Stage.DONE;
        if (list != null && mc.currentScreen == list) {
            mc.displayGuiScreen(title);
        }
        done.finished(outcome, reason, fields);
    }

    /** A field declared by this class or a superclass, by its name, made readable. */
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException notHere) {
                // Looked for in the superclass next.
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** A method of this class or a superclass with that name and number of parameters, made callable. */
    private static Method method(Class<?> type, String name, int parameters) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == parameters) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException(name);
    }
}
