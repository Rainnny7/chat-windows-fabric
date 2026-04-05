package me.braydon.window.chat;

import net.minecraft.network.chat.Component;

/** One captured chat line (full styling) plus the client GUI tick when it was received. */
public record ChatWindowLine(Component styled, int addedGuiTick) {}
