package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.chat.ChatClickCopyHandler;
import me.braydon.chatutilities.chat.ChatSearchUi;
import me.braydon.chatutilities.chat.ChatSearchOverlay;
import me.braydon.chatutilities.chat.ChatSearchState;
import me.braydon.chatutilities.chat.ChatSearchVanillaScrollClamp;
import me.braydon.chatutilities.chat.ChatImagePreviewLayer;
import me.braydon.chatutilities.chat.ChatImagePreviewUrlResolver;
import me.braydon.chatutilities.chat.ChatImagePreviewUrls;
import me.braydon.chatutilities.chat.ChatSearchBarLayout;
import me.braydon.chatutilities.chat.ChatSymbolPalette;
import me.braydon.chatutilities.chat.VanillaChatLinePicker;
import me.braydon.chatutilities.chat.ChatSymbolPaletteLayer;
import me.braydon.chatutilities.chat.ChatUtilitiesHud;
import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.ChatWindowScrollbarInteraction;
import me.braydon.chatutilities.chat.ChatWindowClickHandler;
import me.braydon.chatutilities.chat.ChatWindowHudTabStrip;
import me.braydon.chatutilities.chat.CommandOutgoingAliases;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.command.ChatUtilitiesCommands;
import me.braydon.chatutilities.gui.FullscreenImagePreviewScreen;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

import me.braydon.chatutilities.chat.ServerProfile;

/**
 * Chat screen draws {@link net.minecraft.client.gui.components.CommandSuggestions} after {@code super.render()},
 * so a normal {@code Button} widget was covered. The chip and symbol palette are drawn from Fabric
 * {@link ScreenEvents#afterRender} so they layer above other mods that inject into {@code ChatScreen#render}; clicks
 * are still handled in this mixin.
 *
 * <p>Smooth chat does not redirect {@code ChatScreen}'s bar {@code fill} call: Lunar and similar clients merge or
 * replace that bytecode, and a {@code @Redirect} there fails the entire class transform.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    private static final int chatUtilities$BTN_GAP = 3;
    /** Space between symbol chip and settings chip (smaller than {@link #chatUtilities$BTN_GAP}). */
    private static final int chatUtilities$BTN_GAP_INNER = 1;
    private static final int chatUtilities$BTN_W = 20;
    /** Vanilla font baseline sits slightly low in the chat bar; nudge the chip up for optical centering. */
    private static final int chatUtilities$CHIP_Y_NUDGE = -2;

    @Shadow
    protected EditBox input;

    @Shadow
    @Final
    private CommandSuggestions commandSuggestions;

    /**
     * Lazily created: in rare cases a mixin-applied {@code @Unique} field initializer has been observed as null at
     * runtime (NPE on {@code chatUtilities$palette}); the accessor guarantees a non-null palette.
     */
    @Unique
    private ChatSymbolPalette chatUtilities$palette;

    @Unique
    private ChatSymbolPalette chatUtilities$requirePalette() {
        if (chatUtilities$palette == null) {
            chatUtilities$palette = new ChatSymbolPalette();
        }
        return chatUtilities$palette;
    }

    @Unique
    private boolean chatUtilities$barAnimActive;

    @Unique
    private long chatUtilities$barAnimStartMs;
    @Unique
    private int chatUtilities$currentBarSlideDy;

    @Unique
    private int chatUtilities$inputAnchorY;

    @Unique
    private int chatUtilities$symAnchorY;

    @Unique
    private int chatUtilities$layoutW = Integer.MIN_VALUE;

    @Unique
    private int chatUtilities$layoutH = Integer.MIN_VALUE;

    /** Symbol button bounds in screen space; {@code width == 0} means disabled (narrow chat). */
    @Unique
    private int chatUtilities$symX;

    @Unique
    private int chatUtilities$symY;

    @Unique
    private int chatUtilities$symW;

    @Unique
    private int chatUtilities$symH;

    @Unique
    private int chatUtilities$menuX;

    @Unique
    private int chatUtilities$menuY;

    @Unique
    private int chatUtilities$menuW;

    @Unique
    private int chatUtilities$menuH;

    @Unique
    private int chatUtilities$menuAnchorY;

    @Unique
    private boolean chatUtilities$fabricAfterRenderHooked;

    @Unique
    private EditBox chatUtilities$searchField;

    @Unique
    private int chatUtilities$searchFieldAnchorY;

    /** Search row height: matches the {@link EditBox} and the magnifier slot (see {@link #chatUtilities$searchIconW}). */
    @Unique
    private int chatUtilities$searchBarH = 14;

    /**
     * Width reserved left of the search {@link EditBox} for the magnifier (wider than {@link #chatUtilities$searchBarH}
     * so the icon can draw slightly larger than the text field height).
     */
    @Unique
    private int chatUtilities$searchIconW = 14;

    /**
     * Same artwork as ChatPatches {@code search_button_unfocused} (bundled under chat-utilities assets).
     */
    @Unique
    private static final Identifier CHAT_UTILITIES_SEARCH_ICON =
            Identifier.fromNamespaceAndPath(
                    "chat-utilities", "textures/gui/sprites/search_button.png");

    @Unique
    private static void chatUtilities$drawSearchMagnifier(
            GuiGraphicsExtractor g, int slotLeft, int slotTop, int slotH, int iconColumnW, int unusedArgb) {
        int dim = Math.min(iconColumnW - 2, slotH);
        int x = slotLeft + (iconColumnW - dim) / 2;
        int y = slotTop + (slotH - dim) / 2;
        g.blit(
                RenderPipelines.GUI_TEXTURED,
                CHAT_UTILITIES_SEARCH_ICON,
                x,
                y,
                0f,
                0f,
                dim,
                dim,
                16,
                16);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void chatUtilities$afterInit(CallbackInfo ci) {
        chatUtilities$symW = 0;
        chatUtilities$menuW = 0;
        ChatScreen self = (ChatScreen) (Object) this;
        if (input != null && ChatUtilitiesClientOptions.isShowChatSymbolSelector()) {
            int fullW = input.getWidth();
            boolean menuChip = ChatUtilitiesClientOptions.isShowChatBarMenuButton();
            int shrink =
                    menuChip
                            ? chatUtilities$BTN_GAP
                                    + chatUtilities$BTN_W
                                    + chatUtilities$BTN_GAP_INNER
                                    + chatUtilities$BTN_W
                                    + chatUtilities$BTN_GAP
                            : chatUtilities$BTN_W + chatUtilities$BTN_GAP;
            if (fullW > shrink + 40) {
                input.setWidth(fullW - shrink);
                chatUtilities$symX = input.getX() + input.getWidth() + chatUtilities$BTN_GAP;
                chatUtilities$symW = chatUtilities$BTN_W;
                chatUtilities$symH = Math.max(input.getHeight(), 12);
                chatUtilities$symY =
                        input.getY()
                                + (input.getHeight() - chatUtilities$symH) / 2
                                + chatUtilities$CHIP_Y_NUDGE;
                if (menuChip) {
                    chatUtilities$menuX =
                            chatUtilities$symX
                                    + chatUtilities$symW
                                    + chatUtilities$BTN_GAP_INNER;
                    chatUtilities$menuW = chatUtilities$BTN_W;
                    chatUtilities$menuH = chatUtilities$symH;
                    chatUtilities$menuY = chatUtilities$symY;
                }
            }
        }
        if (input != null && ChatUtilitiesClientOptions.isChatSearchBarEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            ChatComponent chat = mc.gui.getChat();
            int inputH = input.getHeight();
            // Slightly taller than the chat bar so it matches the magnifier slot next to it.
            chatUtilities$searchBarH = Math.max(16, inputH + 4);
            chatUtilities$searchIconW = chatUtilities$searchBarH + 4;
            double sc = chat.getScale();
            int inner = Mth.ceil(chat.getWidth() / sc);
            int chatVisualW = (int) Math.ceil((inner + 12) * sc);
            int marginX = 4;
            int maxBar = Math.min(self.width - marginX * 2, 320);
            int totalBarW =
                    Mth.clamp(
                            Math.round(chatVisualW * 0.58f),
                            chatUtilities$searchIconW + 72,
                            maxBar);
            int searchX = marginX;
            int fieldW = totalBarW - chatUtilities$searchIconW;
            int barY = input.getY();
            int searchY = ChatSearchBarLayout.searchFieldY(mc, barY, chatUtilities$searchBarH);
            chatUtilities$searchField =
                    new EditBox(
                            mc.font,
                            searchX + chatUtilities$searchIconW,
                            searchY,
                            fieldW,
                            chatUtilities$searchBarH,
                            Component.translatable("chat-utilities.chat_search.box"));
            chatUtilities$searchField.setMaxLength(256);
            chatUtilities$searchField.setBordered(true);
            chatUtilities$searchField.setHint(Component.translatable("chat-utilities.chat_search.hint"));
            chatUtilities$searchField.setValue(ChatSearchState.getRawQuery());
            chatUtilities$searchField.setResponder(ChatSearchState::setQuery);
            ((ScreenInvoker) (Object) this).chatUtilities$addRenderableWidget(chatUtilities$searchField);
            ChatSearchUi.bind(chatUtilities$searchField);
            input.setY(barY);
            if (chatUtilities$symW > 0) {
                chatUtilities$symY =
                        input.getY()
                                + (input.getHeight() - chatUtilities$symH) / 2
                                + chatUtilities$CHIP_Y_NUDGE;
            }
            if (chatUtilities$menuW > 0) {
                chatUtilities$menuY =
                        input.getY()
                                + (input.getHeight() - chatUtilities$menuH) / 2
                                + chatUtilities$CHIP_Y_NUDGE;
            }
            commandSuggestions.updateCommandInfo();
        } else {
            chatUtilities$searchField = null;
        }
        if (!chatUtilities$fabricAfterRenderHooked) {
            chatUtilities$fabricAfterRenderHooked = true;
            ScreenEvents.afterExtract(self)
                    .register(
                            (screen, graphics, mouseX, mouseY, partialTick) -> {
                                ChatSymbolPaletteLayer.render(
                                        graphics,
                                        Minecraft.getInstance().font,
                                        screen.width,
                                        screen.height,
                                        mouseX,
                                        mouseY);
                                ChatImagePreviewLayer.render(
                                        graphics,
                                        Minecraft.getInstance().font,
                                        screen.width,
                                        screen.height,
                                        mouseX,
                                        mouseY);
                            });
        }
        chatUtilities$initBarSlideAfterLayout(self);
    }

    @Unique
    private void chatUtilities$initBarSlideAfterLayout(ChatScreen self) {
        chatUtilities$layoutW = self.width;
        chatUtilities$layoutH = self.height;
        chatUtilities$barAnimActive = false;
        if (input == null) {
            return;
        }
        if (!ChatUtilitiesClientOptions.isSmoothChat()) {
            return;
        }
        int dur = ChatUtilitiesClientOptions.getSmoothChatBarOpenMs();
        if (dur <= 0) {
            return;
        }
        chatUtilities$barAnimActive = true;
        chatUtilities$barAnimStartMs = System.currentTimeMillis();
        chatUtilities$inputAnchorY = input.getY();
        chatUtilities$symAnchorY =
                chatUtilities$symW > 0
                        ? input.getY()
                                + (input.getHeight() - chatUtilities$symH) / 2
                                + chatUtilities$CHIP_Y_NUDGE
                        : 0;
        chatUtilities$menuAnchorY =
                chatUtilities$menuW > 0
                        ? input.getY()
                                + (input.getHeight() - chatUtilities$menuH) / 2
                                + chatUtilities$CHIP_Y_NUDGE
                        : 0;
        if (chatUtilities$searchField != null) {
            chatUtilities$searchFieldAnchorY = chatUtilities$searchField.getY();
        }
        chatUtilities$applyBarSlideOffset(chatUtilities$slideDistance());
    }

    @Unique
    private int chatUtilities$slideDistance() {
        return input != null ? Math.max(12, input.getHeight() + 4) : 12;
    }

    /**
     * When the chat search row is enabled, {@code Screen} arrow-key focus navigation can move focus from the main
     * input to the search field on Up. Skip the superclass handler for Up/Down while the main chat field is focused
     * so vanilla message history still works.
     *
     * <p>Uses {@link WrapOperation} (not {@code @Redirect} + {@code @Invoker}) so the wrapped call resolves to
     * {@link Screen#keyPressed} without virtual dispatch back into {@link ChatScreen#keyPressed}.
     */
    @WrapOperation(
            method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screens/Screen;keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"))
    private boolean chatUtilities$skipSuperKeyForChatArrowHistory(
            ChatScreen self, KeyEvent event, Operation<Boolean> original) {
        if (!ChatUtilitiesClientOptions.isChatSearchBarEnabled()
                || chatUtilities$searchField == null
                || input == null) {
            return original.call(self, event);
        }
        int key = event.key();
        if ((key != GLFW.GLFW_KEY_UP && key != GLFW.GLFW_KEY_DOWN) || self.getFocused() != input) {
            return original.call(self, event);
        }
        return false;
    }

    /**
     * When the player presses Tab to request command suggestions, expand a leading {@code /alias} into its target so
     * the vanilla command suggestion pipeline (including completion insertion) operates on the real command.
     */
    @Inject(
            method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z",
            at = @At("HEAD"))
    private void chatUtilities$rewriteAliasBeforeTabComplete(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (input == null || commandSuggestions == null) {
            return;
        }
        if (event.key() != GLFW.GLFW_KEY_TAB) {
            return;
        }
        String before = input.getValue();
        if (before == null || before.isBlank() || !before.stripLeading().startsWith("/")) {
            return;
        }
        ServerProfile p = ChatUtilitiesManager.get().getEffectiveProfileForOutgoingCommands();
        if (p == null || p.getCommandAliases().isEmpty()) {
            return;
        }
        String after = CommandOutgoingAliases.modifySlashChatMessage(before, p);
        if (after == null || after.equals(before)) {
            return;
        }
        input.setValue(after);
        input.moveCursorToEnd(false);
        commandSuggestions.updateCommandInfo();
    }

    @Inject(
            method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void chatUtilities$paletteSearchKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        ChatSymbolPalette palette = chatUtilities$requirePalette();
        if (palette != null && palette.isOpen()) {
            long wh = Minecraft.getInstance().getWindow().handle();
            boolean shift =
                    GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            boolean control =
                    GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            if (palette.keyPressed(event.key(), shift, control)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private void chatUtilities$applyBarSlideOffset(int dy) {
        if (input == null) {
            return;
        }
        chatUtilities$currentBarSlideDy = dy;
        input.setY(chatUtilities$inputAnchorY + dy);
        if (chatUtilities$symW > 0) {
            chatUtilities$symY = chatUtilities$symAnchorY + dy;
        }
        if (chatUtilities$menuW > 0) {
            chatUtilities$menuY = chatUtilities$menuAnchorY + dy;
        }
        if (chatUtilities$searchField != null
                && ChatUtilitiesClientOptions.getChatSearchBarPosition()
                        == ChatUtilitiesClientOptions.ChatSearchBarPosition.BELOW_CHAT) {
            chatUtilities$searchField.setY(chatUtilities$searchFieldAnchorY + dy);
        }
        commandSuggestions.updateCommandInfo();
    }

    @WrapOperation(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void chatUtilities$slideChatBarBackgroundFill(
            GuiGraphicsExtractor graphics,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int color,
            Operation<Void> original) {
        int y1 = minY;
        int y2 = maxY;
        int c = color;
        if (input != null) {
            boolean nearInputBg =
                    minX <= input.getX()
                            && maxX >= input.getX() + input.getWidth()
                            && maxY - minY <= input.getHeight() + 8
                            && minY >= chatUtilities$inputAnchorY - 8
                            && minY <= chatUtilities$inputAnchorY + 8;
            if (nearInputBg) {
                if (chatUtilities$currentBarSlideDy != 0) {
                    y1 += chatUtilities$currentBarSlideDy;
                    y2 += chatUtilities$currentBarSlideDy;
                }
                int a = (c >>> 24) & 0xFF;
                int rgb = c & 0xFFFFFF;
                int na =
                        Mth.clamp(
                                Math.round(
                                        a
                                                * (ChatUtilitiesClientOptions.getChatBarBackgroundOpacityPercent()
                                                        / 100f)),
                                0,
                                255);
                c = (na << 24) | rgb;
            }
        }
        original.call(graphics, minX, y1, maxX, y2, c);
    }

    @Unique
    private void chatUtilities$refreshBarSlide(ChatScreen self) {
        if (input == null) {
            return;
        }
        if (chatUtilities$layoutW != self.width || chatUtilities$layoutH != self.height) {
            chatUtilities$layoutW = self.width;
            chatUtilities$layoutH = self.height;
            chatUtilities$barAnimActive = false;
            chatUtilities$inputAnchorY = input.getY();
            if (chatUtilities$symW > 0) {
                chatUtilities$symY =
                        input.getY()
                                + (input.getHeight() - chatUtilities$symH) / 2
                                + chatUtilities$CHIP_Y_NUDGE;
                chatUtilities$symAnchorY = chatUtilities$symY;
            }
            if (chatUtilities$menuW > 0) {
                chatUtilities$menuY =
                        input.getY()
                                + (input.getHeight() - chatUtilities$menuH) / 2
                                + chatUtilities$CHIP_Y_NUDGE;
                chatUtilities$menuAnchorY = chatUtilities$menuY;
            }
            if (chatUtilities$searchField != null && ChatUtilitiesClientOptions.isChatSearchBarEnabled()) {
                int sy =
                        ChatSearchBarLayout.searchFieldY(
                                Minecraft.getInstance(), input.getY(), chatUtilities$searchBarH);
                chatUtilities$searchField.setY(sy);
                chatUtilities$searchFieldAnchorY = sy;
            }
            commandSuggestions.updateCommandInfo();
        }
        if (!chatUtilities$barAnimActive) {
            return;
        }
        if (!ChatUtilitiesClientOptions.isSmoothChat()) {
            chatUtilities$barAnimActive = false;
            chatUtilities$applyBarSlideOffset(0);
            return;
        }
        int dur = ChatUtilitiesClientOptions.getSmoothChatBarOpenMs();
        if (dur <= 0) {
            chatUtilities$barAnimActive = false;
            chatUtilities$applyBarSlideOffset(0);
            return;
        }
        long elapsed = System.currentTimeMillis() - chatUtilities$barAnimStartMs;
        float t = Mth.clamp(elapsed / (float) dur, 0f, 1f);
        float eased = 1f - (float) Math.pow(1f - t, 3);
        int slide = chatUtilities$slideDistance();
        int dy = Math.round((1f - eased) * slide);
        chatUtilities$applyBarSlideOffset(dy);
        if (t >= 1f) {
            chatUtilities$barAnimActive = false;
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"))
    private void chatUtilities$renderHeadBarSlide(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ChatScreen self = (ChatScreen) (Object) this;
        chatUtilities$refreshBarSlide(self);
        if (chatUtilities$searchField != null && ChatUtilitiesClientOptions.isChatSearchBarEnabled()) {
            chatUtilities$searchField.setEditable(!ChatUtilitiesManager.get().isPositioning());
            if (chatUtilities$searchField.isFocused() && input != null && input.isFocused()) {
                input.setFocused(false);
            }
        }
        Minecraft mc = Minecraft.getInstance();
        ChatSymbolPalette palette = chatUtilities$requirePalette();
        if (palette.isOpen()) {
            palette.tickScrollbarDrag(mc, self.width, self.height);
        }
        // ChatScreen doesn't reliably expose mouseDragged/mouseReleased hooks in 1.21.11; poll mouse state here
        // to drive tab drag-to-reorder.
        Window win = mc.getWindow();
        double mx = mc.mouseHandler.getScaledXPos(win);
        double my = mc.mouseHandler.getScaledYPos(win);
        long wh = win.handle();
        boolean leftDown = GLFW.glfwGetMouseButton(wh, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
        if (leftDown) {
            ChatWindowHudTabStrip.tryHandleDrag(mc, mx, my, 0);
        } else {
            ChatWindowHudTabStrip.tryHandleRelease(mc, mx, my, 0);
        }
        if ((mc.gui.getGuiTicks() & 1) == 0) {
            ChatSearchVanillaScrollClamp.apply(mc);
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void chatUtilities$onRemoved(CallbackInfo ci) {
        if (chatUtilities$palette != null) {
            chatUtilities$palette.setOpen(false);
        }
        chatUtilities$symW = 0;
        chatUtilities$menuW = 0;
        chatUtilities$barAnimActive = false;
        chatUtilities$currentBarSlideDy = 0;
        chatUtilities$layoutW = Integer.MIN_VALUE;
        chatUtilities$layoutH = Integer.MIN_VALUE;
        ChatSearchState.clear();
        ChatSearchUi.clear();
        chatUtilities$searchField = null;
        ChatWindowScrollbarInteraction.clearDrag();
        ChatWindowHudTabStrip.clearDrag();
    }

    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("RETURN"))
    private void chatUtilities$searchUnfocusOnOutsideClick(
            MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0) {
            return;
        }
        if (!ChatUtilitiesClientOptions.isChatSearchBarEnabled() || chatUtilities$searchField == null) {
            return;
        }
        if (!chatUtilities$searchField.isFocused()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        double mx = mc.mouseHandler.getScaledXPos(win);
        double my = mc.mouseHandler.getScaledYPos(win);
        int fx = chatUtilities$searchField.getX();
        int fy = chatUtilities$searchField.getY();
        int fw = chatUtilities$searchField.getWidth();
        int fh = chatUtilities$searchField.getHeight();
        int magLeft = fx - chatUtilities$searchIconW;
        int magRight = fx;
        int magTop = fy;
        int magBottom = fy + chatUtilities$searchBarH;
        boolean inField = mx >= fx && mx < fx + fw && my >= fy && my < fy + fh;
        boolean inMag = mx >= magLeft && mx < magRight && my >= magTop && my < magBottom;
        if (!inField && !inMag) {
            chatUtilities$searchField.setFocused(false);
            ChatScreen self = (ChatScreen) (Object) this;
            if (input != null) {
                input.setFocused(true);
                self.setFocused(input);
            }
        }
    }

    @Unique
    private boolean chatUtilities$symbolHit(double mx, double my) {
        return chatUtilities$symW > 0
                && mx >= chatUtilities$symX
                && mx < chatUtilities$symX + chatUtilities$symW
                && my >= chatUtilities$symY
                && my < chatUtilities$symY + chatUtilities$symH;
    }

    @Unique
    private boolean chatUtilities$menuHit(double mx, double my) {
        return chatUtilities$menuW > 0
                && mx >= chatUtilities$menuX
                && mx < chatUtilities$menuX + chatUtilities$menuW
                && my >= chatUtilities$menuY
                && my < chatUtilities$menuY + chatUtilities$menuH;
    }

    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void chatUtilities$mouseClickedHead(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        ChatScreen self = (ChatScreen) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        double mx = mc.mouseHandler.getScaledXPos(win);
        double my = mc.mouseHandler.getScaledYPos(win);
        if (ChatUtilitiesClientOptions.isImageChatPreviewEnabled()) {
            ChatUtilitiesClientOptions.ClickMouseBinding b =
                    ChatUtilitiesClientOptions.getFullscreenImagePreviewClickBinding();
            long wh = mc.getWindow().handle();
            boolean control =
                    GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            boolean shift =
                    GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            boolean alt =
                    GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
            if (b != null && b.matches(event.button(), control, shift, alt)) {
                int ix = (int) mx;
                int iy = (int) my;
                Optional<String> urlHit = ChatImagePreviewUrlResolver.findPreviewUrl(mc, ix, iy);
                if (urlHit.isPresent()) {
                    Optional<String> norm = ChatImagePreviewUrls.normalizePreviewableHttpUrl(urlHit.get());
                    if (norm.isPresent()) {
                        String n = norm.get();
                        cir.setReturnValue(true);
                        mc.execute(() -> mc.setScreen(new FullscreenImagePreviewScreen(self, n)));
                        return;
                    }
                }
            }
        }
        if (event.button() == 0
                && chatUtilities$searchField != null
                && ChatUtilitiesClientOptions.isChatSearchBarEnabled()
                && ChatSearchOverlay.tryConsumeJumpClick(Minecraft.getInstance(), chatUtilities$searchField, mx, my)) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 0
                && ChatWindowScrollbarInteraction.tryBeginMouseDown(
                        Minecraft.getInstance(), (int) mx, (int) my, event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (!ChatUtilitiesManager.get().isPositioning()
                && ChatClickCopyHandler.tryHandleCopyClick(
                        Minecraft.getInstance(), self, mx, my, event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 0
                && !ChatUtilitiesManager.get().isPositioning()
                && ChatWindowHudTabStrip.tryHandleClick(Minecraft.getInstance(), mx, my, event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 0
                && !ChatUtilitiesManager.get().isPositioning()
                && ChatWindowClickHandler.tryHandleClick(Minecraft.getInstance(), mx, my, event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 0 && chatUtilities$menuHit(mx, my)) {
            chatUtilities$requirePalette().setOpen(false);
            ChatSymbolPalette.playUiClickSound();
            ChatUtilitiesCommands.openMenuNextTick(Minecraft.getInstance());
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 0 && chatUtilities$symbolHit(mx, my)) {
            chatUtilities$requirePalette().toggle();
            ChatSymbolPalette.playUiClickSound();
            cir.setReturnValue(true);
            return;
        }
        ChatSymbolPalette palette = chatUtilities$requirePalette();
        if (!palette.isOpen()) {
            return;
        }
        if (chatUtilities$symbolHit(mx, my)) {
            return;
        }
        if (palette.containsPoint(mx, my, self.width, self.height)) {
            palette.mouseClicked(mx, my, event.button(), input, self.width, self.height);
            cir.setReturnValue(true);
            return;
        }
        palette.setOpen(false);
    }

    @Inject(
            method = "mouseScrolled(DDDD)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void chatUtilities$mouseScrolledHead(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfoReturnable<Boolean> cir) {
        ChatScreen self = (ChatScreen) (Object) this;
        if (chatUtilities$requirePalette()
                .mouseScrolled(mouseX, mouseY, verticalAmount, self.width, self.height)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("RETURN"))
    private void chatUtilities$afterMouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfoReturnable<Boolean> cir) {
        ChatSearchVanillaScrollClamp.apply(Minecraft.getInstance());
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("TAIL"))
    private void chatUtilities$renderOverChatUi(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ChatSymbolPaletteLayer.prepare(
                chatUtilities$requirePalette(),
                chatUtilities$symW > 0,
                chatUtilities$symX,
                chatUtilities$symY,
                chatUtilities$symW,
                chatUtilities$symH,
                chatUtilities$menuW > 0,
                chatUtilities$menuX,
                chatUtilities$menuY,
                chatUtilities$menuW,
                chatUtilities$menuH);
        ChatScreen self = (ChatScreen) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (chatUtilities$searchField != null && ChatUtilitiesClientOptions.isChatSearchBarEnabled()) {
            int col = ChatSearchState.isInvalidPattern() ? 0xFFFF5555 : 0xFFAAAAAA;
            chatUtilities$drawSearchMagnifier(
                    graphics,
                    chatUtilities$searchField.getX() - chatUtilities$searchIconW,
                    chatUtilities$searchField.getY(),
                    chatUtilities$searchBarH,
                    chatUtilities$searchIconW,
                    col);
        }
        ChatSearchOverlay.renderVanillaJump(mc, graphics, mc.font, mouseX, mouseY, self.width, self.height);
        if (commandSuggestions.isVisible()) {
            commandSuggestions.extractRenderState(graphics, mouseX, mouseY);
        }
    }

    // Tooltip suppression is handled in ScreenMixin to avoid brittle ChatScreen bytecode targeting.
}
