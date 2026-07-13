package com.paisetrail.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String) {
    /** The path passed to `navController.navigate(...)` for a plain, argument-free jump (e.g. a
     * bottom-nav tap) — same as [route] unless a destination declares optional query args, since
     * navigating to the raw `{arg}`-containing pattern string wouldn't resolve. */
    open val navRoute: String get() = route

    data object Dashboard : Destination("dashboard")
    data object Map : Destination("map")

    /** [categoryName] is an optional query arg (spec 7.1 "tap a category to see its transactions")
     * — absent for a plain bottom-nav tap, present when arriving pre-filtered from the Dashboard. */
    data object Transactions : Destination("transactions?categoryName={categoryName}") {
        const val ARG_CATEGORY_NAME = "categoryName"
        override val navRoute: String = "transactions"
        fun createRoute(categoryName: String) = "transactions?categoryName=$categoryName"
    }

    data object Trips : Destination("trips")
    data object Settings : Destination("settings")

    /** Pushed from Dashboard's "needs review" badge, not a bottom-nav tab (spec 7.1 / 7.5). */
    data object ReviewQueue : Destination("review_queue")

    /** Pushed from Settings, not a bottom-nav tab (spec 7.6 / 10 Phase 1). */
    data object RawEventsDebug : Destination("raw_events_debug")

    /** Pushed from Settings, not a bottom-nav tab (spec 7.7 category icon/edit). */
    data object CategoryManagement : Destination("category_management")

    /** Pushed from a row in [com.paisetrail.app.ui.screens.trips.TripsScreen] (spec 7.4). */
    data object TripDetail : Destination("trip_detail/{tripId}") {
        const val ARG_TRIP_ID = "tripId"
        fun createRoute(tripId: Long) = "trip_detail/$tripId"
    }

    /** Pushed from a row in the Transactions list (spec 7.3). */
    data object TransactionDetail : Destination("transaction_detail/{txnId}") {
        const val ARG_TXN_ID = "txnId"
        fun createRoute(txnId: Long) = "transaction_detail/$txnId"
    }

    /** Pushed from Settings (spec 7.6 debug tools). */
    data object RandomTransaction : Destination("random_transaction")

    /** Pushed from Settings (spec 7.6 debug tools). */
    data object ManualTransaction : Destination("manual_transaction")

    /** Pushed from Settings (spec 3.2 "data, not code"). */
    data object BankPatternManagement : Destination("bank_pattern_management")

    /** Pushed from Settings (spec 4.2 merchant learning). */
    data object MerchantManagement : Destination("merchant_management")

    /** Pushed from the Dashboard's "Insights →" link — the 30-day chart, budgets, and month-end
     * forecast live here instead of crowding the Home glance. */
    data object Insights : Destination("insights")
}

data class BottomNavItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector,
)

val BottomNavItems = listOf(
    BottomNavItem(Destination.Dashboard, "Home", Icons.Outlined.Home),
    BottomNavItem(Destination.Map, "Map", Icons.Outlined.Map),
    BottomNavItem(Destination.Transactions, "Transactions", Icons.AutoMirrored.Outlined.List),
    BottomNavItem(Destination.Trips, "Trips", Icons.Outlined.Star),
    BottomNavItem(Destination.Settings, "Settings", Icons.Outlined.Settings),
)
