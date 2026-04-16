package me.braydon.chatutilities.gui;

import me.braydon.chatutilities.chat.ProfileFaviconCache;
import me.braydon.chatutilities.chat.ServerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Multiplayer-style profile rows with icon, title, and subtitle; clipped + scrollable. */
public final class ServerProfileListWidget {
    public static final int ROW_HEIGHT = 36;

    private final Minecraft minecraft;
    private final Consumer<ServerProfile> onOpenProfile;
    private final Runnable onSelectionChanged;
    private String selectedProfileId;
    private int x;
    private int y;
    private int width;
    private int height;
    private int scroll;
    private final List<ServerProfile> profiles = new ArrayList<>();

    public ServerProfileListWidget(
            Minecraft minecraft,
            Consumer<ServerProfile> onOpenProfile,
            Runnable onSelectionChanged) {
        this.minecraft = minecraft;
        this.onOpenProfile = onOpenProfile;
        this.onSelectionChanged = onSelectionChanged;
    }

    /** @return the selected profile in the current list, or null */
    public ServerProfile getSelectedProfile() {
        if (selectedProfileId == null) {
            return null;
        }
        for (ServerProfile p : profiles) {
            if (p.getId().equals(selectedProfileId)) {
                return p;
            }
        }
        return null;
    }

    public void clearSelection() {
        if (selectedProfileId != null) {
            selectedProfileId = null;
            onSelectionChanged.run();
        }
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    public void replaceProfiles(List<ServerProfile> next) {
        profiles.clear();
        profiles.addAll(next);
        if (selectedProfileId != null
                && profiles.stream().noneMatch(p -> p.getId().equals(selectedProfileId))) {
            selectedProfileId = null;
            onSelectionChanged.run();
        }
        clampScroll();
    }

    private void clampScroll() {
        int max = Math.max(0, profiles.size() * ROW_HEIGHT - height);
        scroll = Mth.clamp(scroll, 0, max);
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int totalHeight = profiles.size() * ROW_HEIGHT;
        int maxScroll = Math.max(0, totalHeight - height);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        graphics.enableScissor(x, y, x + width, y + height);
        try {
            if (profiles.isEmpty()) {
                String line1 = "No profiles yet.";
                String line2 = "A profile holds chat windows, ignores, and message sounds";
                String line3 = "for the servers you add. Create one to begin.";
                int cx = x + width / 2;
                int cy = y + height / 2 - 16;
                graphics.centeredText(minecraft.font, line1, cx, cy, ChatUtilitiesScreenLayout.TEXT_GRAY);
                graphics.centeredText(minecraft.font, line2, cx, cy + 11, ChatUtilitiesScreenLayout.TEXT_GRAY_DARK);
                graphics.centeredText(minecraft.font, line3, cx, cy + 22, ChatUtilitiesScreenLayout.TEXT_GRAY_DARK);
            }
            for (int i = 0; i < profiles.size(); i++) {
                int rowY = y + i * ROW_HEIGHT - scroll;
                if (rowY + ROW_HEIGHT < y || rowY > y + height) {
                    continue;
                }
                ServerProfile p = profiles.get(i);
                boolean hovered =
                        mouseX >= x
                                && mouseX < x + width
                                && mouseY >= rowY
                                && mouseY < rowY + ROW_HEIGHT;
                boolean selected =
                        selectedProfileId != null && selectedProfileId.equals(p.getId());
                drawRow(graphics, p, x, rowY, width, hovered, selected);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawRow(
            GuiGraphicsExtractor graphics, ServerProfile p, int rx, int ry, int rw, boolean hovered, boolean selected) {
        if (selected) {
            graphics.fill(rx, ry, rx + rw, ry + ROW_HEIGHT, 0x408080FF);
        }
        if (hovered) {
            graphics.fill(rx, ry, rx + rw, ry + ROW_HEIGHT, 0x33FFFFFF);
        }
        int iconX = rx + 2;
        int iconY = ry + ROW_HEIGHT / 2 - 16;
        graphics.fill(iconX, iconY, iconX + 32, iconY + 32, 0xFF3C3C3C);
        graphics.fill(iconX, iconY, iconX + 32, iconY + 1, 0xFF8E8E8E);
        graphics.fill(iconX, iconY + 31, iconX + 32, iconY + 32, 0xFF1E1E1E);
        String firstHost = p.getServers().isEmpty() ? null : p.getServers().getFirst();
        Identifier favicon = ProfileFaviconCache.getIcon(minecraft, firstHost);
        if (favicon != null) {
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    favicon,
                    iconX,
                    iconY,
                    0f,
                    0f,
                    32,
                    32,
                    32,
                    32);
        } else {
            graphics.item(new ItemStack(Items.COMPASS), iconX + 8, iconY + 8);
        }
        int textX = iconX + 32 + 4;
        String title = p.getDisplayName();
        if (title.length() > 42) {
            title = title.substring(0, 39) + "...";
        }
        graphics.text(
                minecraft.font,
                Component.literal(title),
                textX,
                ry + 4,
                ChatUtilitiesScreenLayout.TEXT_WHITE,
                false);
        String detail = summarizeServersAndStats(p);
        if (detail.length() > 58) {
            detail = detail.substring(0, 55) + "...";
        }
        graphics.text(
                minecraft.font,
                Component.literal(detail),
                textX,
                ry + 17,
                ChatUtilitiesScreenLayout.TEXT_GRAY,
                false);
    }

    private static String summarizeServersAndStats(ServerProfile p) {
        return summarizeServers(p) + " · " + profileStats(p);
    }

    private static String profileStats(ServerProfile p) {
        int w = p.getWindows().size();
        int ign = 0;
        int snd = 0;
        int hl = 0;
        for (me.braydon.chatutilities.chat.ChatActionGroup g : p.getChatActionGroups()) {
            for (me.braydon.chatutilities.chat.ChatActionEffect e : g.getEffects()) {
                if (e.getType() == me.braydon.chatutilities.chat.ChatActionEffect.Type.IGNORE) {
                    ign++;
                } else if (e.getType() == me.braydon.chatutilities.chat.ChatActionEffect.Type.PLAY_SOUND) {
                    snd++;
                } else if (e.getType() == me.braydon.chatutilities.chat.ChatActionEffect.Type.COLOR_HIGHLIGHT) {
                    hl++;
                }
            }
        }
        return w
                + (w == 1 ? " window" : " windows")
                + ", "
                + ign
                + (ign == 1 ? " ignore" : " ignores")
                + ", "
                + snd
                + (snd == 1 ? " sound" : " sounds")
                + ", "
                + hl
                + (hl == 1 ? " highlight" : " highlights");
    }

    private static String summarizeServers(ServerProfile p) {
        List<String> s = p.getServers();
        if (s.isEmpty()) {
            return "All servers";
        }
        if (s.size() == 1) {
            return s.getFirst();
        }
        if (s.size() == 2) {
            return s.get(0) + ", " + s.get(1);
        }
        return s.get(0) + ", " + s.get(1) + " +" + (s.size() - 2);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, boolean doubleClick) {
        if (button != 0) {
            return false;
        }
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        int relY = (int) mouseY - y + scroll;
        int idx = relY / ROW_HEIGHT;
        if (idx >= 0 && idx < profiles.size()) {
            ServerProfile p = profiles.get(idx);
            if (doubleClick) {
                onOpenProfile.accept(p);
            } else {
                String id = p.getId();
                if (!id.equals(selectedProfileId)) {
                    selectedProfileId = id;
                    onSelectionChanged.run();
                }
            }
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        int max = Math.max(0, profiles.size() * ROW_HEIGHT - height);
        scroll = Mth.clamp(scroll - (int) (vertical * ROW_HEIGHT / 2.0), 0, max);
        return true;
    }
}
