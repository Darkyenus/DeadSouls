# DeadSouls
*Dark Souls inspired graveyard plugin*

- Run `./wemi assembly` to compile
- Plugin jar is in `/build/DeadSouls-<version>.jar`, install it appropriately. Spigot 1.14.2 or later is needed.
- Die (preferably only in-game)
- Go where you died, you will find a soul with all your items and most of your XP (90% by default)
    - Soul is collected automatically by walking over it
    - Souls make sounds, so it is easier to find them
    - In the first hour (configurable), only you can see/hear the soul and collect its items, after then anybody can
        - If you want your friends to take it instead, you can release your soul by clicking the button sent in the chat
    - If you don't have enough space in your inventory, you will collect only what you can carry. You can return for the rest later.
- The soul will never disappear (configurable)

The plugin has been designed to be reliable and lightweight. It does not use any non-public API.
The plugin does not do anything that you wouldn't expect it to do, so it should be fairly compatible
with a wide variety of other plugins. If you don't know whether a plugin is compatible, just try it! It will probably work.
Let me know if you encounter any incompatibilities.

This plugin does not track, snoop, or otherwise call home or anywhere else.

## API and compatibility considerations
*For developers of other plugins*

This plugins relies on the `PlayerDeathEvent` to do its job.
This event is handled at `HIGH` priority and respects *keep inventory* and *keep level* flags.
Items collected into the soul are taken from the drop table, which is then cleared, and similarly for XP.

If you want to prevent some items from dropping, remove them in a lower priority event handler.

There are no other event handlers that could cause compatibility problems.

The plugin provides a public stable API (since version 1.6), which can be obtained through `DeadSoulsAPI.instance()`.
NOTE: Read the documentation for possible failure modes.

To compile against this plugin, get the dependency here: [![](https://jitpack.io/v/com.darkyen/DeadSouls.svg)](https://jitpack.io/#com.darkyen/DeadSouls).
