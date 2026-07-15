package com.paisetrail.app.interaction

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.enrich.TagConfirmationUseCase
import com.paisetrail.app.util.formatRupees
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The "forced popup" mode spec 5 describes as optional (`SYSTEM_ALERT_WINDOW`, off by default in
 * the plan) — drawn directly on top of whatever's on screen via [WindowManager.addView] rather
 * than the notification tray, since it doesn't depend on the OS choosing to surface a heads-up
 * notification (unreliable on OEMs that aggressively background-kill this app). Plain Android
 * views, not Compose — a raw window added outside an Activity has no natural
 * Lifecycle/SavedStateRegistry owner to host a ComposeView. */
@Singleton
class TagPromptOverlay @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tagConfirmationUseCase: TagConfirmationUseCase,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentOverlayView: View? = null
    private var dismissRunnable: Runnable? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    fun show(txn: TransactionEntity, tripName: String? = null, predictions: List<String>) {
        if (!canShow()) return
        mainHandler.post { showOnMainThread(txn, tripName, predictions) }
    }

    private fun showOnMainThread(txn: TransactionEntity, tripName: String?, predictions: List<String>) {
        dismiss()

        val windowManager = context.getSystemService(WindowManager::class.java) ?: return

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 48, 56, 48)
            background = GradientDrawable().apply {
                setColor(SURFACE_COLOR)
                cornerRadius = 32f
            }
            isClickable = true
        }

        card.addView(
            TextView(context).apply {
                text = buildString {
                    append(formatRupees(txn.amountPaise))
                    txn.payeeNameRaw?.let { append(" → $it") }
                }
                setTextColor(INK_COLOR)
                textSize = 18f
            },
        )
        card.addView(
            TextView(context).apply {
                text = if (tripName != null) "Tag this payment · $tripName" else "Tag this payment"
                setTextColor(INK_MUTED_COLOR)
                textSize = 13f
                setPadding(0, 8, 0, 0)
            },
        )

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 32, 0, 0)
        }
        predictions.forEach { categoryName ->
            buttonRow.addView(
                Button(context).apply {
                    text = categoryName
                    setOnClickListener { applyTag(txn.id, categoryName) }
                },
            )
        }
        card.addView(buttonRow)

        // A false-positive capture (a promo that slipped through, a duplicate) is most obvious
        // right when this popup appears — deleting it shouldn't require a trip into the app.
        card.addView(
            TextView(context).apply {
                text = "Delete"
                setTextColor(NEGATIVE_COLOR)
                textSize = 14f
                setPadding(0, 24, 0, 0)
                setOnClickListener { deleteTxn(txn.id) }
            },
        )

        // Full-screen transparent root so tapping outside the card dismisses it, same as a modal
        // dialog — the card itself absorbs its own clicks so they don't bubble through to that.
        val root = FrameLayout(context).apply {
            setBackgroundColor(SCRIM_COLOR)
            isClickable = true
            setOnClickListener { dismiss() }
            addView(
                card,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )

        try {
            windowManager.addView(root, windowParams)
            currentOverlayView = root
        } catch (e: Exception) {
            Log.e(TAG, "failed to add tag prompt overlay window", e)
            return
        }

        val runnable = Runnable { dismiss() }
        dismissRunnable = runnable
        mainHandler.postDelayed(runnable, AUTO_DISMISS_MS)
    }

    private fun applyTag(txnId: Long, categoryName: String) {
        ioScope.launch { tagConfirmationUseCase.confirmCategory(txnId, categoryName) }
        mainHandler.post { dismiss() }
    }

    private fun deleteTxn(txnId: Long) {
        ioScope.launch { tagConfirmationUseCase.deleteTransaction(txnId) }
        mainHandler.post { dismiss() }
    }

    private fun dismiss() {
        dismissRunnable?.let(mainHandler::removeCallbacks)
        dismissRunnable = null
        currentOverlayView?.let { view ->
            try {
                context.getSystemService(WindowManager::class.java)?.removeView(view)
            } catch (e: Exception) {
                // Already removed (e.g. dismissed then timed out) — nothing to do.
            }
        }
        currentOverlayView = null
    }

    companion object {
        private const val TAG = "TagPromptOverlay"
        private const val AUTO_DISMISS_MS = 10 * 60 * 1000L
        private const val SURFACE_COLOR = 0xFF16181B.toInt()
        private const val INK_COLOR = 0xFFE8E8E4.toInt()
        private const val INK_MUTED_COLOR = 0xFF8A8D93.toInt()
        private const val NEGATIVE_COLOR = 0xFFE0707A.toInt()
        private const val SCRIM_COLOR = 0x99000000.toInt()
    }
}
