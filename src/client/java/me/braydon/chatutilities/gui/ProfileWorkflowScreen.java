package me.braydon.chatutilities.gui;

import net.minecraft.client.gui.screens.Screen;

/**
 * Screens in the profile flow that {@link WindowManageScreen} can restore after in-world position
 * mode.
 */
public interface ProfileWorkflowScreen {
    ChatUtilitiesRootScreen getChatRoot();

    /** Fresh screen for the same profile (used when returning from position mode). */
    Screen recreateForProfile();
}
