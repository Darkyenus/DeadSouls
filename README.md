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