package com.paisetrail.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.paisetrail.app.ui.screens.banks.BankPatternManagementScreen
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

@Composable
fun PaisaTrailNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            NavigationBar {
                BottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.destination.route,
                        onClick = {
                            navController.navigate(item.destination.navRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = {
                            Text(
                                text = item.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding),
        ) {
            composable(Destination.Dashboard.route) {
                DashboardScreen(
                    onNavigateToReviewQueue = { navController.navigate(Destination.ReviewQueue.route) },
                    onNavigateToInsights = { navController.navigate(Destination.Insights.route) },
                    onNavigateToTransactionsFiltered = { categoryName ->
                        navController.navigate(Destination.Transactions.createRoute(categoryName)) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Destination.Map.route) { MapScreen() }
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
                InsightsScreen(onBack = { navController.popBackStack() })
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
}
