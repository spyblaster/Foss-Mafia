package org.fossify.messages.mafia

import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.messages.activities.SimpleActivity

/**
 * Base class for every mafia-game screen.
 *
 * SimpleActivity enables edge-to-edge drawing. We need to re-apply
 * system-bar insets as padding on the window decor view so the content
 * is never obscured by the status bar or navigation bar.
 *
 * We set the listener in onStart() (not onCreate) so it fires AFTER
 * SimpleActivity's own window setup completes.
 */
abstract class BaseGameActivity : SimpleActivity() {

    override fun onStart() {
        super.onStart()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
