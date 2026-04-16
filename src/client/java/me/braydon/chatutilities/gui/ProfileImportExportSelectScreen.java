package me.braydon.chatutilities.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.ServerProfile;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Minimal in-game selector for import/export: click rows to toggle.
 * Avoids extra UI deps (checkbox widget) and keeps the native file pickers.
 */
public final class ProfileImportExportSelectScreen extends Screen {
    private static final int PAD = 12;
    private static final int ROW_H = 18;
    private static final int HEADER_H = 54;
    private static final int FOOTER_H = 40;

    private final Screen back;
    private final Mode mode;
    private final Path defaultDir;

    private final List<Row> rows = new ArrayList<>();
    private int scrollY;

    private String importJson;

    private enum Mode {
        EXPORT,
        IMPORT
    }

    private static final class Row {
        final String id;
        final String label;
        boolean checked;
        final boolean enabled;

        Row(String id, String label, boolean checked, boolean enabled) {
            this.id = id;
            this.label = label;
            this.checked = checked;
            this.enabled = enabled;
        }
    }

    private ProfileImportExportSelectScreen(Screen back, Mode mode) {
        super(Component.literal(mode == Mode.EXPORT ? "Export Profiles" : "Import Profiles"));
        this.back = back;
        this.mode = mode;
        this.defaultDir = FabricLoader.getInstance().getConfigDir().resolve("chat-utilities");
    }

    public static Screen forExport(Screen back, boolean includeSettingsDefault) {
        ProfileImportExportSelectScreen s = new ProfileImportExportSelectScreen(back, Mode.EXPORT);
        // Settings toggle first.
        s.rows.add(new Row("settings", "Include settings", includeSettingsDefault, true));
        for (ServerProfile p : ChatUtilitiesManager.get().getProfiles()) {
            String name = p.getDisplayName() != null && !p.getDisplayName().isBlank() ? p.getDisplayName().strip() : p.getId();
            s.rows.add(new Row("profile:" + p.getId(), name, true, true));
        }
        return s;
    }

    public static Screen forImport(Screen back, String json, boolean includeSettingsDefault) {
        ProfileImportExportSelectScreen s = new ProfileImportExportSelectScreen(back, Mode.IMPORT);
        s.importJson = json;
        boolean settingsPresent = false;
        JsonArray profilesArr = null;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("settings")) {
                    settingsPresent = true;
                }
                if (obj.has("profiles")) {
                    profilesArr = obj.getAsJsonArray("profiles");
                } else if (obj.has("profiles") && obj.get("profiles").isJsonArray()) {
                    profilesArr = obj.getAsJsonArray("profiles");
                }
                // Raw export is the same shape as chat-utilities.json (RootV3): it always has "profiles".
                if (profilesArr == null && obj.has("profiles") && obj.get("profiles").isJsonArray()) {
                    profilesArr = obj.getAsJsonArray("profiles");
                }
                if (profilesArr == null && obj.has("profiles")) {
                    try {
                        profilesArr = obj.getAsJsonArray("profiles");
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        s.rows.add(new Row("settings", "Import settings", includeSettingsDefault && settingsPresent, settingsPresent));

        if (profilesArr == null) {
            // Let the import path handle errors; still show settings-only toggle if present.
            return s;
        }
        for (int i = 0; i < profilesArr.size(); i++) {
            JsonElement pe = profilesArr.get(i);
            if (!pe.isJsonObject()) {
                continue;
            }
            JsonObject po = pe.getAsJsonObject();
            String id = po.has("id") ? po.get("id").getAsString() : ("idx:" + i);
            String name = po.has("displayName") ? po.get("displayName").getAsString() : id;
            s.rows.add(new Row("importProfileIndex:" + i, name, true, true));
        }
        return s;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int w = this.width;
        int h = this.height;

        g.fill(0, 0, w, h, 0xD0101010);

        int titleY = PAD;
        g.text(this.font, this.title, PAD, titleY, 0xFFFFFFFF, false);
        g.text(
                this.font,
                Component.literal("Click rows to toggle. Enter to confirm, Esc to cancel."),
                PAD,
                titleY + 14,
                0xFFB8C0CC,
                false);

        int listTop = HEADER_H;
        int listBottom = h - FOOTER_H;
        g.enableScissor(0, listTop, w, listBottom);
        try {
            int y = listTop + 6 - scrollY;
            for (Row r : rows) {
                int x0 = PAD;
                int x1 = w - PAD;
                int y0 = y;
                int y1 = y + ROW_H;
                boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
                int bg =
                        !r.enabled
                                ? 0x40202020
                                : hover
                                        ? 0x80303038
                                        : 0x60202028;
                int edge = !r.enabled ? 0x50404048 : 0xFF50505A;
                g.fill(x0, y0, x1, y1, bg);
                g.outline(x0, y0, x1 - x0, y1 - y0, edge);
                String mark = r.enabled ? (r.checked ? "[x] " : "[ ] ") : "[-] ";
                int tc = r.enabled ? 0xFFE8EEF8 : 0xFF888890;
                g.text(this.font, mark + r.label, x0 + 6, y0 + (ROW_H - 8) / 2, tc, false);
                y += ROW_H + 4;
            }
        } finally {
            g.disableScissor();
        }

        // Footer hint
        g.fill(0, h - FOOTER_H, w, h, 0xC0101010);
        g.text(
                this.font,
                Component.literal(mode == Mode.EXPORT ? "Export will also copy JSON to clipboard." : "Import uses the selected items only."),
                PAD,
                h - FOOTER_H + 12,
                0xFFB8C0CC,
                false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (button != 0) {
            return false;
        }
        int w = this.width;
        int h = this.height;
        int listTop = HEADER_H;
        int listBottom = h - FOOTER_H;
        int x0 = PAD;
        int x1 = w - PAD;
        int mouseX = (int) mx;
        int mouseY = (int) my;
        if (mouseX < x0 || mouseX >= x1 || mouseY < listTop || mouseY >= listBottom) {
            return false;
        }
        int y = listTop + 6 - scrollY;
        for (Row r : rows) {
            int y0 = y;
            int y1 = y + ROW_H;
            if (mouseY >= y0 && mouseY < y1) {
                if (r.enabled) {
                    r.checked = !r.checked;
                }
                return true;
            }
            y += ROW_H + 4;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int contentH = rows.size() * (ROW_H + 4);
        int viewH = Math.max(1, this.height - HEADER_H - FOOTER_H - 12);
        int max = Math.max(0, contentH - viewH);
        scrollY = Mth.clamp(scrollY - (int) Math.signum(verticalAmount) * 18, 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 256) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(back);
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            applySelection();
            return true;
        }
        return super.keyPressed(event);
    }

    private void applySelection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        if (mode == Mode.EXPORT) {
            boolean includeSettings = isChecked("settings");
            Set<String> profileIds = selectedProfileIdsForExport();
            String json = buildExportJson(profileIds, includeSettings);
            mc.keyboardHandler.setClipboard(json);
            Optional<Path> dest = ProfileJsonFileDialog.pickSaveJson(defaultDir, "profiles-export.json");
            if (dest.isPresent()) {
                try {
                    Path p = dest.get();
                    Path parent = p.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.writeString(p, json, StandardCharsets.UTF_8);
                    showToast(
                            net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.export_profiles.toast.title"),
                            p.toString());
                } catch (IOException e) {
                    showToast(
                            net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.export_profiles.toast.error_title"),
                            e.getMessage() != null ? e.getMessage() : "");
                }
            } else {
                showToast(
                        net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.export_profiles.toast.title"),
                        net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.export_profiles.toast.clipboard_only.detail"));
            }
            mc.setScreen(back);
            return;
        }

        // IMPORT
        String json = importJson;
        if (json == null || json.isBlank()) {
            mc.setScreen(back);
            return;
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonArray profilesArr = obj.has("profiles") ? obj.getAsJsonArray("profiles") : null;
            if (profilesArr != null) {
                JsonArray filtered = new JsonArray();
                for (int i = 0; i < profilesArr.size(); i++) {
                    if (!isChecked("importProfileIndex:" + i)) {
                        continue;
                    }
                    filtered.add(profilesArr.get(i));
                }
                JsonObject root = new JsonObject();
                root.add("profiles", filtered);
                int added = ChatUtilitiesManager.get().importProfilesFromJson(root.toString());
                if (isChecked("settings") && obj.has("settings")) {
                    try {
                        ChatUtilitiesClientOptions.importPersistentOptionsFromJson(obj.get("settings").toString());
                    } catch (IOException ignored) {
                    }
                }
                if (added == 0) {
                    showToast(
                            net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.import_profiles.toast.none.title"),
                            net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.import_profiles.toast.none.detail"));
                } else {
                    showToast(
                            net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.import_profiles.toast.ok.title"),
                            net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.import_profiles.toast.ok.detail", added));
                }
                if (back instanceof ChatUtilitiesRootScreen cr) {
                    cr.init();
                }
            } else {
                // Fallback: try importing as-is (may be raw RootV3)
                int added = ChatUtilitiesManager.get().importProfilesFromJson(json);
                showToast(
                        net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.import_profiles.toast.ok.title"),
                        net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.import_profiles.toast.ok.detail", added));
                if (back instanceof ChatUtilitiesRootScreen cr) {
                    cr.init();
                }
            }
        } catch (JsonParseException e) {
            showToast(
                    net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.import_profiles.toast.invalid.title"),
                    net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.import_profiles.toast.invalid.detail"));
        } catch (Exception e) {
            showToast(
                    net.minecraft.client.resources.language.I18n.get("chat-utilities.settings.import_profiles.toast.error.title"),
                    e.getMessage() != null ? e.getMessage() : "");
        } finally {
            mc.setScreen(back);
        }
    }

    private void showToast(String title, String detail) {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        SystemToast.add(
                mc.getToastManager(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal(title),
                Component.literal(detail));
    }

    private boolean isChecked(String id) {
        for (Row r : rows) {
            if (r.id.equals(id)) {
                return r.checked && r.enabled;
            }
        }
        return false;
    }

    private Set<String> selectedProfileIdsForExport() {
        Set<String> out = new HashSet<>();
        for (Row r : rows) {
            if (!r.enabled || !r.checked) {
                continue;
            }
            if (r.id.startsWith("profile:")) {
                out.add(r.id.substring("profile:".length()));
            }
        }
        return out;
    }

    private String buildExportJson(Set<String> profileIds, boolean includeSettings) {
        JsonObject root;
        try {
            root = JsonParser.parseString(ChatUtilitiesManager.get().serializeProfilesToJson()).getAsJsonObject();
        } catch (Exception e) {
            root = new JsonObject();
        }
        if (root.has("profiles")) {
            try {
                JsonArray in = root.getAsJsonArray("profiles");
                JsonArray filtered = new JsonArray();
                for (JsonElement e : in) {
                    if (!e.isJsonObject()) {
                        continue;
                    }
                    JsonObject po = e.getAsJsonObject();
                    if (!po.has("id")) {
                        continue;
                    }
                    String id = po.get("id").getAsString();
                    if (profileIds.contains(id)) {
                        filtered.add(po);
                    }
                }
                root.add("profiles", filtered);
            } catch (Exception ignored) {
            }
        }
        if (includeSettings) {
            JsonObject combined = new JsonObject();
            combined.add("profiles", root.get("profiles"));
            combined.add("settings", JsonParser.parseString(ChatUtilitiesClientOptions.serializePersistentOptionsToJson()));
            return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(combined);
        }
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
}

