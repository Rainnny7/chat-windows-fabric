package me.braydon.chatutilities.chat;

import net.minecraft.network.chat.Component;

public record ChatWindowLine(Component styled, int addedGuiTick) {}
