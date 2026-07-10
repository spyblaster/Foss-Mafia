---
name: telegram-bot-api-android
description: Integrate Telegram Bot API in Android app for sending and receiving messages with OkHttp, proper threading, network-restricted environment handling, polling, multi-step conversations, and input validation (REMOVED from this project on 2026-07-10)
source: auto-skill
extracted_at: '2026-06-26T16:40:09.235Z'
updated_at: '2026-07-10T08:40:52.794Z'
---

# Telegram Bot API Integration for Android

> **REMOVED FROM THIS PROJECT (2026-07-10)**: All Telegram bot functionality has been removed from the messages-app. Players are now saved/retrieved without Telegram user IDs, and all role notifications are sent via SMS only. This skill document is preserved as a reference pattern for future projects that may need Telegram integration.

## Context
When building Android apps that need to send messages via Telegram (e.g., notifying users of game roles, OTP codes, alerts), you need to integrate with the Telegram Bot API. This requires careful handling of network operations, threading, network restrictions, message polling, and multi-step user conversations.

## Prerequisites
- A Telegram bot token (obtained from [@BotFather](https://t.me/botfather))
- OkHttp client configured with timeouts
- User's Telegram chat IDs (obtained when users interact with your bot)

## Core Implementation

### 1. OkHttp Client Setup
Create a singleton or activity-level OkHttp client with timeouts:
```kotlin
private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(35, TimeUnit.SECONDS)
    .build()
```

### 2. Send Message Function (Sync)
```kotlin
private fun sendTelegramMessageSync(chatId: Long, text: String, botToken: String) {
    val url = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=${URLEncoder.encode(text, "UTF-8")}"
    val req = Request.Builder().url(url).build()
    val resp = httpClient.newCall(req).execute()
    if (!resp.isSuccessful) {
        throw Exception("Telegram API returned ${resp.code}")
    }
}
```

### 3. Send Message Function (Async)
```kotlin
private fun sendTelegramMessage(chatId: Long, text: String) {
    Thread {
        try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=${URLEncoder.encode(text, "UTF-8")}"
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute()
        } catch (e: Exception) { }
    }.start()
}
```

## Receiving Messages with Polling

### 4. Polling Setup
Track polling state and last update ID:
```kotlin
private var pollingActive = false
private var lastUpdateId: Long = 0L
private val pollingHandler = Handler(Looper.getMainLooper())
```

### 5. Start Polling
Initialize polling and get the latest update ID:
```kotlin
private fun startPolling() {
    if (botToken.isEmpty()) {
        pollingActive = false
        return
    }
    pollingActive = true
    Thread {
        try {
            val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=-1"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val parsed = mafiaGson.fromJson(body, Map::class.java)
            val result = (parsed["result"] as? List<*>)
            if (!result.isNullOrEmpty()) {
                val last = result.last() as? Map<*, *>
                lastUpdateId = (last?.get("update_id") as? Double)?.toLong() ?: 0L
            }
        } catch (e: Exception) { }
        pollingHandler.post { pollLoop() }
    }.start()
}
```

### 6. Poll Loop (Main Polling Logic)
```kotlin
private fun pollLoop() {
    if (!pollingActive) return
    Thread {
        try {
            val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}&timeout=4"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val parsed = mafiaGson.fromJson(body, Map::class.java)
            val result = (parsed["result"] as? List<*>) ?: emptyList<Any>()

            for (item in result) {
                val update = item as? Map<*, *> ?: continue
                lastUpdateId = (update["update_id"] as? Double)?.toLong() ?: lastUpdateId
                val message = update["message"] as? Map<*, *> ?: continue
                val text = (message["text"] as? String)?.trim() ?: continue
                val from = message["from"] as? Map<*, *> ?: continue
                val userId = (from["id"] as? Double)?.toLong() ?: continue

                // Post to main thread for UI updates
                pollingHandler.post { handleTelegramMessage(text, userId) }
            }
        } catch (e: Exception) { }

        if (pollingActive) pollingHandler.postDelayed({ pollLoop() }, 5000)
    }.start()
}
```

### 7. Auto-Start Polling
Start polling automatically when activity starts (if token exists):
```kotlin
private fun setupMainUI() {
    // ... UI setup ...

    // Start polling automatically
    if (botToken.isNotEmpty()) {
        startPolling()
    }
}
```

### 8. Stop Polling
Stop polling when activity ends:
```kotlin
private fun stopPolling() {
    pollingActive = false
    pollingHandler.removeCallbacksAndMessages(null)
}

override fun onDestroy() {
    super.onDestroy()
    stopPolling()
}

override fun onSupportNavigateUp(): Boolean {
    stopPolling()
    finish()
    return true
}
```

## Multi-Step Conversations (State Machine)

### 9. Track Conversation State
Use a map to track users in multi-step conversations:
```kotlin
// Track users waiting for phone number input
private val awaitingPhone = mutableMapOf<Long, Player>()
```

### 10. Handle Multi-Step Message Flow
```kotlin
private fun handleTelegramMessage(text: String, userId: Long) {
    // Check if this user is waiting for phone number input
    val awaitingPlayer = awaitingPhone[userId]
    if (awaitingPlayer != null) {
        // Validate phone number: must be 11 digits starting with 09
        val phoneRegex = Regex("^09\\d{9}$")
        if (phoneRegex.matches(text)) {
            // Valid phone number, update player
            val idx = players.indexOf(awaitingPlayer)
            if (idx >= 0) {
                players[idx] = awaitingPlayer.copy(phone = text)
                savePlayersToPrefs()
                refreshList()
            }
            awaitingPhone.remove(userId)
            sendTelegramMessage(userId, "شماره موبایل شما با موفقیت ثبت شد: $text")
        } else {
            sendTelegramMessage(userId, "شماره موبایل درست نیست، لطفا فقط شماره موبایل را با اعداد لاتین بدون فاصله و کاراکتر ارسال کنید.")
        }
        return
    }

    // New registration: check for Persian name
    val persianRegex = Regex("^[\\u0600-\\u06FF\\s]{3,20}$")
    if (!persianRegex.matches(text)) {
        sendTelegramMessage(userId, "لطفا فقط نام خود را به زبان فارسی وارد کنید.")
        return
    }

    val name = text.trim()

    if (players.any { it.telegramId == userId }) {
        sendTelegramMessage(userId, "شما قبلا ثبت نام کرده‌اید.")
        return
    }

    if (players.any { it.name == name }) {
        sendTelegramMessage(userId, "این نام قبلا ثبت شده است.")
        return
    }

    // Create player and add to awaiting list for phone number
    val player = Player(name = name, phone = "", telegramId = userId)
    awaitingPhone[userId] = player
    players.add(player)
    players.sortWith { a, b -> persianCollator.compare(a.name, b.name) }
    savePlayersToPrefs()
    refreshList()

    sendTelegramMessage(userId, "ثبت نام شما با موفقیت انجام شد. لطفا شماره موبایل خود را وارد کنید.\n\nفقط یه عدد 11 رقمی مورد قبوله که با 09 شروع بشه، مثلا: 09123456789")
}
```

## Input Validation Patterns

### 11. Persian Name Validation
```kotlin
val persianRegex = Regex("^[\\u0600-\\u06FF\\s]{3,20}$")
if (!persianRegex.matches(text)) {
    sendTelegramMessage(userId, "لطفا فقط نام خود را به زبان فارسی وارد کنید.")
    return
}
```

### 12. Iranian Phone Number Validation
```kotlin
val phoneRegex = Regex("^09\\d{9}$")
if (!phoneRegex.matches(text)) {
    sendTelegramMessage(userId, "شماره موبایل نامعتبر است. فرمت صحیح: 09XXXXXXXXX")
    return
}
```

## Network Operations on Background Thread
**CRITICAL**: Network operations must NOT run on the main thread. Use background threads:
```kotlin
Thread {
    try {
        sendTelegramMessageSync(chatId, message, botToken)
        // Success
    } catch (e: Exception) {
        e.printStackTrace()
        // Handle failure
    }

    // Post result back to main thread for UI updates
    Handler(Looper.getMainLooper()).post {
        // Update UI (show dialog, toast, etc.)
    }
}.start()
```

## Loading Dialog During Network Calls
Show a non-cancelable loading dialog while sending:
```kotlin
val loadingDialog = MaterialAlertDialogBuilder(context)
    .setTitle("در حال ارسال...")
    .setView(CircularProgressIndicator(context))
    .setCancelable(false)
    .create()
loadingDialog.show()

// On background thread...
Handler(Looper.getMainLooper()).post {
    loadingDialog.dismiss()
    // Show result dialog
}
```

## Network-Restricted Environments (e.g., Iran)

### The Problem
In regions where Telegram is blocked, direct API calls fail with:
```
SocketTimeoutException: failed to connect to api.telegram.org/149.154.166.110 (port 443)
```

### Solution
Users must enable VPN and ensure the app is routed through the VPN. Per-app VPN configurations may not route all app traffic.

### Fallback Strategy
Consider adding SMS fallback for critical messages when Telegram fails:

```kotlin
val botToken = prefs.getString("bot_token", "") ?: ""
if (botToken.isNotEmpty()) {
    try {
        sendTelegramMessageSync(chatId, message, botToken)
        telegramSent++
    } catch (e: Exception) {
        telegramFailed++
        // Optionally fallback to SMS here
    }
}
```

## Multi-Channel Notification Logic

When supporting multiple notification channels (SMS, Telegram):

```kotlin
val withPhone = players.filter { it.phone.isNotBlank() }
val withTelegram = players.filter { it.telegramId != 0L }
val withoutContact = players.filter { it.phone.isBlank() && it.telegramId == 0L }

// Priority logic: Telegram takes precedence
val toSendViaTelegram = withTelegram.filter { it.phone.isBlank() } +
                         withTelegram.filter { it.phone.isNotBlank() }
val toSendViaSms = withPhone.filter { it.telegramId == 0L }
```

## Chat ID Storage

### 13. Persisting Telegram IDs
Store user chat IDs alongside their data:
```kotlin
data class Player(
    val name: String,
    val phone: String = "",
    val telegramId: Long = 0L  // 0 means not registered via Telegram
) : Serializable
```

### 14. Saving with Gson
```kotlin
private fun savePlayersToPrefs() {
    getSharedPreferences("mafia_players", Context.MODE_PRIVATE)
        .edit()
        .putString("players", mafiaGson.toJson(players))
        .apply()
}
```

## Best Practices

1. **Always encode message text**: Use `URLEncoder.encode(text, "UTF-8")` for Persian/Farsi text
2. **Handle network timeouts gracefully**: Show meaningful error messages
3. **Don't block UI**: All network operations on background threads
4. **Store bot token securely**: Use SharedPreferences, not hardcoded
5. **Test with VPN**: In restricted regions, verify VPN routes app traffic
6. **Track success/failure counters**: Report statistics to users after bulk sends
7. **Auto-start polling**: When activity starts and token exists
8. **Stop polling on exit**: Clean up polling handlers to prevent memory leaks
9. **Use state machine for conversations**: Track user progress in multi-step flows
10. **Validate all inputs**: Use regex patterns for phone numbers, names, etc.

## Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `SocketTimeoutException` | Network blocked/VPN not routing app | Enable VPN, ensure app is in allowed apps list |
| `NetworkOnMainThreadException` | Network call on UI thread | Move to background thread |
| UnknownHostException | DNS failure for blocked domain | VPN routing issue, not a code bug |
| 401 Unauthorized | Invalid bot token | Verify token with BotFather |
| 400 Bad Request | Invalid parameters | Check chat_id format and URL encoding |
| Polling not receiving messages | `lastUpdateId` not tracking | Initialize with `getUpdates?offset=-1` first |
| Duplicate updates received | Not incrementing `lastUpdateId` | Always update `lastUpdateId` after processing |

## Related Code Files
- `GameActivity.kt` - Polling implementation, message handling, multi-step conversations
- `ResultActivity.kt` - Bulk sending with loading dialogs and error handling
- `RoleActivity.kt` - Telegram ID persistence in data models

## Telegram Bot API Endpoints Used
- `getUpdates` - Receive incoming messages (polling)
- `sendMessage` - Send messages to users