package com.darkyen.minecraft;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.World;

final class SpigotCompat {

    private SpigotCompat() {}

    private static boolean _getMinHeight = true;

    /** Return the minimal possible block height in the world. */
    public static int worldGetMinHeight(World world) {
        if (_getMinHeight) {
            try {
                return world.getMinHeight();
            } catch (Throwable ignored) {}
            _getMinHeight = false;
        }
        return 0;
    }

    private static boolean _textComponent = true;

    /** Set the hover event to text. */
    @SuppressWarnings("deprecation")
    public static void textComponentSetHoverText(TextComponent textComponent, String tooltip) {
        if (_textComponent) {
            try {
                textComponent.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT, new Text(tooltip)));
                return;
            } catch (Throwable ignored) {}
            _textComponent = false;
        }

        textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[]{new TextComponent(tooltip)}));
    }
}
