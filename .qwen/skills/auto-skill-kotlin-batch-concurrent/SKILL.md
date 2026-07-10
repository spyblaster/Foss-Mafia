---
name: kotlin-batch-concurrent
description: Send messages in batches with concurrent execution using Kotlin coroutines
source: auto-skill
extracted_at: '2026-07-01T18:06:01Z'
---

# Batched concurrent operations with Kotlin coroutines

When you need to send multiple messages (SMS, Telegram, etc.) in batches with concurrent execution within each batch and delays between batches.

## Pattern

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Inside a lifecycleScope.launch(Dispatchers.IO) or similar
messages.chunked(25).forEachIndexed { batchIndex, batch ->
    if (batchIndex > 0) {
        delay(1100) // 1.1 second delay between batches
    }
    
    val results = kotlinx.coroutines.coroutineScope {
        batch.map { item ->
            async(Dispatchers.IO) {
                try {
                    // Send message operation
                    sendMessage(item)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }.map { it.await() }
    }
    
    results.forEach { success ->
        if (success) successCount++ else failCount++
    }
}
```

## Key points

1. **Required imports**: `kotlinx.coroutines.async`, `kotlinx.coroutines.delay`
2. **Scope context**: `async` must be called within a `CoroutineScope`. Wrap the batch operations in `kotlinx.coroutines.coroutineScope { }` when already inside a `launch` block
3. **Pattern**: Use `.map { async { ... } }.map { it.await() }` to launch all operations concurrently then collect results
4. **Batching**: Use `.chunked(n)` to split list into batches of n items
5. **Delay**: Call `delay(ms)` between batches, not within the concurrent operations

## Common mistakes

- Calling `async` directly in `launch` block without wrapping in `coroutineScope` → compilation error "Cannot infer type parameter"
- Forgetting to import `async` and `delay` → "Unresolved reference" errors
- Using `Thread.sleep()` instead of `delay()` → blocks the thread instead of suspending
- Putting delay inside the `async` blocks → delays each operation instead of between batches

## Why this pattern

- **Concurrent within batch**: All items in a batch execute simultaneously (up to thread pool limits)
- **Sequential between batches**: Enforces rate limiting by waiting between batches
- **Non-blocking**: Uses coroutine `delay` instead of thread-blocking sleep
- **Error isolation**: Each operation can fail independently; results collected after all complete
