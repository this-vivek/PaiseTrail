package com.paisetrail.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.navArgument
import com.paisetrail.app.ui.components.GlassBar
import com.paisetrail.app.ui.screens.banks.BankPatternManagementScreen
import com.paisetrail.app.ui.screens.budget.BudgetScreen
import com.paisetrail.app.ui.screens.categories.CategoryManagementScreen
import com.paisetrail.app.ui.screens.dashboard.DashboardScreen
import com.paisetrail.app.ui.screens.debug.ManualTransactionScreen
import com.paisetrail.app.ui.screens.debug.RandomTransactionScreen
import com.paisetrail.app.ui.screens.debug.RawEventsDebugScreen
import com.paisetrail.app.ui.screens.insights.InsightsScreen
import com.paisetrail.app.ui.screens.map.MapScreen
import com.paisetrail.app.ui.screens.merchants.MerchantManagementScreen
import com.paisetrail.app.ui.screens.reviewqueue.ReviewQueueScreen
import com.paisetrail.app.ui.screens.settings.SettingsScreen
import com.paisetrail.app.ui.screens.trips.TripDetailScreen
import com.paisetrail.app.ui.screens.trips.TripsScreen
import com.paisetrail.app.ui.screens.transactions.TransactionDetailScreen
import com.paisetrail.app.ui.screens.transactions.TransactionsScreen
import com.paisetrail.app.ui.theme.PaisaMotion
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.ui.theme.PillShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** Bottom nav reserved height (64dp bar + 16dp top/bottom margins) — screens don't need to know
 * this; [NavHost] gets this much bottom padding directly so content never sits behind the floating
 * pill, the same guarantee a Scaffold bottomBar would give, but the bar itself floats with rounded
 * corners and margins rather than spanning full-bleed. */
private val BOTTOM_NAV_RESERVED_HEIGHT = 96.dp

/** Bottom-nav tab routes get a quick fade-through (no slide — they're siblings, not a push/pop
 * stack); everything else gets a slide+fade, mirrored on the way back (spec §2.5). */
private val BOTTOM_NAV_ROUTES = BottomNavItems.map { it.destination.route }.toSet()

private fun isTabSwitch(scope: AnimatedContentTransitionScope<NavBackStackEntry>): Boolean =
    scope.initialState.destination.route in BOTTOM_NAV_ROUTES && scope.targetState.destination.route in BOTTOM_NAV_ROUTES

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navEnterTransition(): EnterTransition =
    if (isTabSwitch(this)) {
        fadeIn(tween(210))
    } else {
        slideInHorizontally(animationSpec = tween(280)) { fullWidth -> fullWidth / 20 } + fadeIn(tween(280))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navExitTransition(): ExitTransition =
    if (isTabSwitch(this)) {
        fadeOut(tween(90))
    } else {
        slideOutHorizontally(animationSpec = tween(280)) { fullWidth -> -fullWidth / 20 } + fadeOut(tween(280))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopEnterTransition(): EnterTransition =
    if (isTabSwitch(this)) {
        fadeIn(tween(210))
    } else {
        slideInHorizontally(animationSpec = tween(280)) { fullWidth -> -fullWidth / 20 } + fadeIn(tween(280))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopExitTransition(): ExitTransition =
    if (isTabSwitch(this)) {
        fadeOut(tween(90))
    } else {
        slideOutHorizontally(animationSpec = tween(280)) { fullWidth -> fullWidth / 20 } + fadeOut(tween(280))
    }

@Composable
fun PaisaTrailNavHost() {
    val navController = rememberNavController()
    val hazeState = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize()) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState).statusBarsPadding()) {
            NavHost(
                navController = navController,
                startDestination = Destination.Dashboard.route,
                // The pill itself sits navigationBarsPadding() above the true bottom edge, plus
                // its own margins/height — content needs the same total clearance or the last
                // rows on a screen end up hidden behind it.
                modifier = Modifier.navigationBarsPadding().padding(bottom = BOTTOM_NAV_RESERVED_HEIGHT),
                enterTransition = { navEnterTransition() },
                exitTransition = { navExitTransition() },
                popEnterTransition = { navPopEnterTransition() },
                popExitTransition = { navPopExitTransition() },
            ) {
                composable(Destination.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToReviewQueue = { navController.navigate(Destination.ReviewQueue.route) },
                        onNavigateToInsights = { navController.navigate(Destination.Insights.route) },
                        onNavigateToBudget = { navController.navigate(Destination.Budget.route) },
                        onNavigateToTransactionsFiltered = { categoryName ->
                            navController.navigate(Destination.Transactions.createRoute(categoryName)) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(Destination.Map.route) {
                    MapScreen(
                        onNavigateToTransaction = { txnId ->
                            navController.navigate(Destination.TransactionDetail.createRoute(txnId))
                        },
                    )
                }
                composable(
                    route = Destination.Transactions.route,
                    arguments = listOf(
                        navArgument(Destination.Transactions.ARG_CATEGORY_NAME) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    TransactionsScreen(
                        onNavigateToTransaction = { txnId ->
                            navController.navigate(Destination.TransactionDetail.createRoute(txnId))
                        },
                    )
                }
                composable(Destination.Trips.route) {
                    TripsScreen(
                        onNavigateToTrip = { tripId ->
                            navController.navigate(Destination.TripDetail.createRoute(tripId))
                        },
                    )
                }
                composable(Destination.Settings.route) {
                    SettingsScreen(
                        onNavigateToRawEventsDebug = { navController.navigate(Destination.RawEventsDebug.route) },
                        onNavigateToReviewQueue = { navController.navigate(Destination.ReviewQueue.route) },
                        onNavigateToCategoryManagement = { navController.navigate(Destination.CategoryManagement.route) },
                        onNavigateToRandomTransaction = { navController.navigate(Destination.RandomTransaction.route) },
                        onNavigateToManualTransaction = { navController.navigate(Destination.ManualTransaction.route) },
                        onNavigateToBankPatternManagement = { navController.navigate(Destination.BankPatternManagement.route) },
                        onNavigateToMerchantManagement = { navController.navigate(Destination.MerchantManagement.route) },
                    )
                }
                composable(Destination.ReviewQueue.route) { ReviewQueueScreen() }
                composable(Destination.Insights.route) {
                    InsightsScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToBudget = { navController.navigate(Destination.Budget.route) },
                    )
                }
                composable(Destination.Budget.route) {
                    BudgetScreen(onBack = { navController.popBackStack() })
                }
                composable(Destination.RawEventsDebug.route) {
                    RawEventsDebugScreen(onBack = { navController.popBackStack() })
                }
                composable(Destination.CategoryManagement.route) {
                    CategoryManagementScreen(onBack = { navController.popBackStack() })
                }
                composable(Destination.RandomTransaction.route) {
                    RandomTransactionScreen(onDone = { navController.popBackStack() })
                }
                composable(Destination.ManualTransaction.route) {
                    ManualTransactionScreen(onDone = { navController.popBackStack() })
                }
                composable(Destination.BankPatternManagement.route) {
                    BankPatternManagementScreen(onBack = { navController.popBackStack() })
                }
                composable(Destination.MerchantManagement.route) {
                    MerchantManagementScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Destination.TripDetail.route,
                    arguments = listOf(navArgument(Destination.TripDetail.ARG_TRIP_ID) { type = NavType.LongType }),
                ) {
                    TripDetailScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Destination.TransactionDetail.route,
                    arguments = listOf(navArgument(Destination.TransactionDetail.ARG_TXN_ID) { type = NavType.LongType }),
                ) {
                    TransactionDetailScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        FloatingBottomNav(
            currentRoute = currentRoute,
            hazeState = hazeState,
            onNavigate = { destination ->
                navController.navigate(destination.navRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

/** Floating glass pill, 16dp from the screen edges/bottom, with a sliding accent indicator that
 * springs between items (spec §4.1). Filled icon + label when selected; outline icon only
 * otherwise. */
@Composable
private fun FloatingBottomNav(
    currentRoute: String?,
    hazeState: HazeState,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = BottomNavItems.indexOfFirst { it.destination.route == currentRoute }.coerceAtLeast(0)

    GlassBar(
        hazeState = hazeState,
        shape = PillShape,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .fillMaxWidth()
            .height(64.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / BottomNavItems.size
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = PaisaMotion.springBouncy(),
                label = "navIndicator",
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .padding(6.dp)
                    .width(itemWidth - 12.dp)
                    .height(maxHeight - 12.dp)
                    .background(PaisaTheme.colors.accent.copy(alpha = 0.16f), PillShape),
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomNavItems.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    // A fixed-width slot per tab, matching the indicator's own itemWidth grid —
                    // Arrangement.SpaceEvenly used to size each slot by its own wrap-content width,
                    // which differs for the selected tab (it also shows a label), so the sliding
                    // indicator and the actual tab positions drifted out of sync.
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onNavigate(item.destination) },
                        contentAlignment = Alignment.Center,
                    ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = item.label,
                            tint = if (selected) PaisaTheme.colors.accent else PaisaTheme.colors.inkMuted,
                        )
                        if (selected) {
                            Text(
                                text = item.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = PaisaTheme.typography.caption,
                                color = PaisaTheme.colors.accent,
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}
