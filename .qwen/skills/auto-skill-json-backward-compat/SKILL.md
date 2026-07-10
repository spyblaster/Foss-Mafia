---
name: json-backward-compat
description: Evolve JSON export format by adding fields while maintaining import compatibility with old files
source: auto-skill
extracted_at: '2026-07-01T18:53:54Z'
---

# Backward-compatible JSON format evolution

When you need to add new fields to an exported JSON format but still support importing files saved in the old format.

## Pattern

```kotlin
// Old format: just a list
// [ { "name": "Alice", "phone": "..." }, ... ]

// New format: wrapper with additional fields
data class Export(
    val items: List<Item>,
    val metadata: String = ""
)

// Export: always use new format
fun export(uri: Uri) {
    val export = Export(items = items, metadata = currentMetadata)
    outputStream.write(gson.toJson(export).toByteArray())
}

// Import: try new format first, fall back to old
fun import(uri: Uri) {
    val json = inputStream.readText()
    
    // Try new format first
    val export: Export? = try {
        gson.fromJson(json, Export::class.java)
    } catch (e: Exception) {
        null
    }
    
    val importedItems: List<Item> = if (export != null) {
        // New format: extract items and metadata
        if (export.metadata.isNotEmpty()) {
            currentMetadata = export.metadata
            saveMetadata()
        }
        export.items
    } else {
        // Old format: direct list deserialization
        val type = object : TypeToken<List<Item>>() {}.type
        gson.fromJson(json, type)
    }
    
    // Process imported items
    items.addAll(importedItems)
}
```

## Key points

1. **Export always uses new format** - Don't check version or conditionally export old format
2. **Import tries new format first** - Attempt to parse as new wrapper object
3. **Graceful fallback** - Catch deserialization exception and try old format
4. **Extract new fields when present** - Only update metadata/settings if they exist in imported file
5. **TypeToken for generic lists** - Use `TypeToken<List<T>>()` pattern for old format deserialization

## Applied example (Telegram bot token in players export)

```kotlin
// New wrapper format
data class PlayersExport(
    val players: List<Player>,
    val botToken: String = ""
)

fun loadPlayersFromUri(uri: Uri) {
    val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
    
    // Try new format with bot token
    val export: PlayersExport? = try {
        gson.fromJson(json, PlayersExport::class.java)
    } catch (e: Exception) {
        null
    }
    
    val importedPlayers: List<Player> = if (export != null) {
        // New format: load players and bot token
        if (export.botToken.isNotEmpty()) {
            botToken = export.botToken
            prefs.edit().putString("bot_token", botToken).apply()
        }
        export.players
    } else {
        // Old format: only players list
        val type = object : TypeToken<List<Player>>() {}.type
        gson.fromJson(json, type)
    }
    
    players.addAll(importedPlayers.filter { p -> 
        players.none { it.name == p.name }
    })
}
```

## Why this pattern

- **No version field needed** - Structure difference is detectable by deserialization success/failure
- **Old files still work** - Users can import files saved before the format change
- **Smooth migration** - No manual file conversion step required
- **Future-proof** - Additional fields can be added to wrapper without breaking existing importers

## Common mistakes

- Checking file version before parsing → adds complexity and failure modes
- Always trying old format first → new fields ignored even when present
- Throwing on old format → breaks existing user files
- Not using try/catch for format detection → deserialization exceptions crash the app
