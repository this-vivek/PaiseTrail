package com.paisetrail.app.ui.screens.map

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.ui.components.formatIndianRupees

/** One pin (spec 7.2) — color=category, size proportional to amount, both applied by the caller
 * via [categoryColorHex]/[txn].amountPaise since [ClusterItem] itself only carries position and
 * text. */
class TxnClusterItem(
    val txn: TransactionEntity,
    val categoryColorHex: String?,
    val categoryEmoji: String? = null,
) : ClusterItem {
    private val latLng = LatLng(txn.lat!!, txn.lng!!)

    override fun getPosition(): LatLng = latLng
    override fun getTitle(): String = txn.payeeNameRaw ?: txn.vpa ?: "Unknown"
    override fun getSnippet(): String = formatIndianRupees(txn.amountPaise)
    override fun getZIndex(): Float = 0f
}
