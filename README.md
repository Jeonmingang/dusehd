# LobbySelector (Paper 1.21.x, Java 21)

- Java 21
- Paper API 1.21.1
- Citizens API (optional, for NPC linking)
- Adventure Components (Item meta & GUI title)

## Build
```bash
mvn -U -DskipTests clean package
```
Output: `target/LobbySelector-1.1.1.jar`

## Commands
- `/servermenu` : Open GUI
- `/npcgui link|unlink|list` : Link the Citizens NPC you're looking at to open GUI on right click
