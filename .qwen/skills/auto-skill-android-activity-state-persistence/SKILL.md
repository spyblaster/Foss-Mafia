---
name: android-activity-state-persistence
description: Debug and fix Android activity lifecycle state persistence issues when data changes don't survive navigation
source: auto-skill
extracted_at: '2026-07-02T11:20:26Z'
---

# Android Activity State Persistence Debugging

## Problem Pattern

When changes made in one Android Activity don't persist when navigating back to another Activity, even though the data is being saved to disk/JSON.

## Common Root Causes

1. **Activity not reloading on resume**: The returning Activity keeps stale in-memory data instead of reloading from persistent storage
2. **Different serialization instances**: Activities using different Gson/serialization instances causing compatibility issues
3. **Data ordering mismatch**: Index-based updates where load order differs from save order

## Diagnostic Steps

### 1. Verify data is actually being saved
- Add logging to save operations to confirm they execute
- Check file timestamps to ensure writes are happening
- Inspect the saved JSON/data file content

### 2. Check Activity lifecycle
- Verify `onResume()` in the returning Activity
- Check if it reloads data from disk or keeps in-memory state
- Look for `saveGame()` without corresponding `loadGame()` calls

### 3. Check serialization consistency
- Ensure all Activities use the same serialization instance (e.g., shared `mafiaGson` instead of separate `Gson()` instances)
- Verify data class fields match between save and load operations

### 4. Verify data structure alignment
- If using index-based updates, ensure load order matches save order
- Check that the data being updated corresponds to the correct record

## Solution Pattern

### Fix the Activity lifecycle reload

In the Activity that needs to reflect changes made elsewhere:

```kotlin
override fun onResume() {
    super.onResume()
    if (::dataList.isInitialized) {
        // Reload from disk BEFORE any other operations
        val file = File(filesDir, "data.json")
        if (file.exists()) {
            try {
                val savedData = gson.fromJson<SavedData>(file.readText(), ...)
                dataList.clear()
                dataList.addAll(savedData.items)
                refreshUI()  // Update the UI with reloaded data
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading data", e)
            }
        }
        // THEN do other operations like auto-save
        saveData()
    }
}
```

### Key principles

1. **Reload before save**: In `onResume()`, reload from disk FIRST, then save if needed
2. **Shared serialization**: Use a single shared Gson/serializer instance across Activities
3. **Refresh UI**: After reloading data, explicitly refresh the UI to reflect changes
4. **Error handling**: Log errors to help diagnose serialization issues

## Example Scenario

**Problem**: User clicks hearts in `ManageActivity` to toggle player lives. Changes save to JSON. But when returning to `ResultActivity`, all hearts are red again.

**Root cause**: `ResultActivity.onResume()` only called `saveGame()` but never reloaded from disk. It kept stale `resultList` in memory.

**Fix**: Add reload logic at the start of `onResume()` to refresh `resultList` from the saved JSON file before any other operations.

## When to Apply

- State changes in Activity A don't appear in Activity B after navigation
- Data is confirmed to be saving to disk successfully
- The returning Activity shows stale/cached data
- Multiple Activities share and modify the same persistent data
