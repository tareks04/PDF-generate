package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.i18n.ThemePreference
import com.example.ui.components.LanguageTogglePill
import com.example.ui.screens.BackupScreen
import com.example.ui.screens.ChapterDetailScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HelpScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PdfPreviewScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SmartPageLibraryTheme
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.PdfPreviewViewModel
import com.example.viewmodel.ScannerViewModel

object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val SCANNER = "scanner"
    const val LIBRARY = "library"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val CHAPTER_DETAIL = "chapter/{chapterId}"
    const val PDF_PREVIEW = "pdf/{chapterId}"

    fun chapterDetail(chapterId: String) = "chapter/$chapterId"
    fun pdfPreview(chapterId: String) = "pdf/$chapterId"
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SmartLibraryApp).container
        setContent {
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(container))
            val scannerViewModel: ScannerViewModel = viewModel(factory = ScannerViewModel.Factory(container))
            val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(container))

            val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
            val strings by mainViewModel.strings.collectAsStateWithLifecycle()
            val allChapters by mainViewModel.allChapters.collectAsStateWithLifecycle()
            val metrics by mainViewModel.dashboardMetrics.collectAsStateWithLifecycle()

            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (preferences.theme) {
                ThemePreference.SYSTEM -> systemDark
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
            }

            SmartPageLibraryTheme(darkTheme = isDarkTheme) {
                SmartPageLibraryAppContent(
                    isDarkTheme = isDarkTheme,
                    mainViewModel = mainViewModel,
                    scannerViewModel = scannerViewModel,
                    libraryViewModel = libraryViewModel,
                    preferences = preferences,
                    strings = strings,
                    allChapters = allChapters,
                    metrics = metrics,
                    container = container,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartPageLibraryAppContent(
    isDarkTheme: Boolean,
    mainViewModel: MainViewModel,
    scannerViewModel: ScannerViewModel,
    libraryViewModel: LibraryViewModel,
    preferences: com.example.data.repository.AppPreferences,
    strings: com.example.i18n.AppStrings,
    allChapters: List<com.example.data.local.ChapterWithStats>,
    metrics: com.example.viewmodel.DashboardMetrics,
    container: com.example.di.AppContainer,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoutes.DASHBOARD

    val scannerUiState by scannerViewModel.uiState.collectAsStateWithLifecycle()
    val showSavedOnly by libraryViewModel.showSavedOnly.collectAsStateWithLifecycle()

    val isFullScreenSubRoute = currentRoute.startsWith("chapter/") || currentRoute.startsWith("pdf/")

    val bottomItems = listOf(
        NavDestinationItem(AppRoutes.DASHBOARD, strings.navDashboard, Icons.Default.Dashboard, "nav_dashboard"),
        NavDestinationItem(AppRoutes.SCANNER, strings.navScanner, Icons.Default.QrCodeScanner, "nav_scanner"),
        NavDestinationItem(AppRoutes.LIBRARY, strings.navLibrary, Icons.AutoMirrored.Filled.MenuBook, "nav_library"),
        NavDestinationItem(AppRoutes.BACKUP, strings.navBackup, Icons.Default.Backup, "nav_backup"),
    )

    val railItems = bottomItems + listOf(
        NavDestinationItem(AppRoutes.SETTINGS, strings.navSettings, Icons.Default.Settings, "nav_settings"),
        NavDestinationItem(AppRoutes.HELP, strings.navHelp, Icons.AutoMirrored.Filled.HelpOutline, "nav_help"),
    )

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(AppRoutes.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedWidth = maxWidth >= 720.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (!isFullScreenSubRoute) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        title = {
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        actions = {
                            LanguageTogglePill(
                                isBangla = strings.isBangla,
                                contentDescription = strings.toggleLangContentDesc,
                                onToggle = mainViewModel::toggleLanguage,
                            )
                            IconButton(
                                onClick = { mainViewModel.toggleQuickTheme(isDarkTheme) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("theme_toggle_button"),
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = strings.toggleThemeContentDesc,
                                )
                            }
                            if (!isExpandedWidth) {
                                var showOverflowMenu by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("topbar_more_menu"),
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = strings.navSettings)
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strings.navSettings) },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            navigateTopLevel(AppRoutes.SETTINGS)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.navHelp) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            navigateTopLevel(AppRoutes.HELP)
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (!isFullScreenSubRoute && !isExpandedWidth) {
                    NavigationBar {
                        bottomItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = { navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag(item.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (!isFullScreenSubRoute && isExpandedWidth) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        railItems.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = { navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag(item.testTag),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.DASHBOARD,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    composable(AppRoutes.DASHBOARD) {
                        DashboardScreen(
                            metrics = metrics,
                            strings = strings,
                            onNavigateToScanner = { navigateTopLevel(AppRoutes.SCANNER) },
                            onNavigateToLibrary = { navigateTopLevel(AppRoutes.LIBRARY) },
                            onOpenChapter = { chapterId ->
                                navController.navigate(AppRoutes.chapterDetail(chapterId))
                            },
                            onOpenPdfPreview = { chapterId ->
                                navController.navigate(AppRoutes.pdfPreview(chapterId))
                            },
                        )
                    }

                    composable(AppRoutes.SCANNER) {
                        ScannerScreen(
                            uiState = scannerUiState,
                            chaptersWithStats = allChapters,
                            strings = strings,
                            viewModel = scannerViewModel,
                            onRenameChapter = libraryViewModel::renameChapter,
                        )
                    }

                    composable(AppRoutes.LIBRARY) {
                        LibraryScreen(
                            chaptersWithStats = allChapters,
                            showSavedOnly = showSavedOnly,
                            strings = strings,
                            onToggleSavedOnly = libraryViewModel::setShowSavedOnly,
                            onOpenChapter = { chapterId ->
                                navController.navigate(AppRoutes.chapterDetail(chapterId))
                            },
                            onOpenPdfPreview = { chapterId ->
                                navController.navigate(AppRoutes.pdfPreview(chapterId))
                            },
                            onRenameChapter = libraryViewModel::renameChapter,
                            onDeleteChapterLinks = libraryViewModel::clearChapterLinks,
                        )
                    }

                    composable(AppRoutes.BACKUP) {
                        BackupScreen(
                            strings = strings,
                            libraryViewModel = libraryViewModel,
                        )
                    }

                    composable(AppRoutes.SETTINGS) {
                        SettingsScreen(
                            preferences = preferences,
                            strings = strings,
                            onSelectLanguage = mainViewModel::setLanguage,
                            onSelectTheme = mainViewModel::setTheme,
                            onSaveScannerSettings = mainViewModel::saveScannerSettings,
                        )
                    }

                    composable(AppRoutes.HELP) {
                        HelpScreen(strings = strings)
                    }

                    composable(
                        route = AppRoutes.CHAPTER_DETAIL,
                        arguments = listOf(navArgument("chapterId") { type = NavType.StringType }),
                    ) { entry ->
                        val chapterId = entry.arguments?.getString("chapterId") ?: return@composable
                        val chapter by libraryViewModel.observeChapter(chapterId).collectAsStateWithLifecycle(initialValue = null)
                        val links by libraryViewModel.observeChapterLinks(chapterId).collectAsStateWithLifecycle(initialValue = emptyList())

                        ChapterDetailScreen(
                            chapter = chapter,
                            links = links,
                            strings = strings,
                            onBack = { navController.popBackStack() },
                            onOpenPdfPreview = { navController.navigate(AppRoutes.pdfPreview(chapterId)) },
                            onRenameChapter = { title -> libraryViewModel.renameChapter(chapterId, title) },
                            onDeleteSingleLink = { id -> libraryViewModel.deleteSingleLink(chapterId, id) },
                            onDeleteSelectedLinks = { ids -> libraryViewModel.deleteSelectedLinks(chapterId, ids) },
                            onDeleteAllLinks = { libraryViewModel.clearChapterLinks(chapterId) },
                        )
                    }

                    composable(
                        route = AppRoutes.PDF_PREVIEW,
                        arguments = listOf(navArgument("chapterId") { type = NavType.StringType }),
                    ) { entry ->
                        val chapterId = entry.arguments?.getString("chapterId") ?: return@composable
                        val pdfViewModel: PdfPreviewViewModel = viewModel(
                            key = "pdf_$chapterId",
                            factory = PdfPreviewViewModel.Factory(container),
                        )
                        val pdfState by pdfViewModel.state.collectAsStateWithLifecycle()
                        val chapterTitle by pdfViewModel.chapterDisplayTitle.collectAsStateWithLifecycle()
                        val pageSizeMode by pdfViewModel.pageSizeMode.collectAsStateWithLifecycle()

                        PdfPreviewScreen(
                            chapterId = chapterId,
                            chapterTitle = chapterTitle,
                            state = pdfState,
                            pageSizeMode = pageSizeMode,
                            strings = strings,
                            onLoadChapter = { id ->
                                pdfViewModel.loadChapterAndBuildPdf(id, strings.isBangla)
                            },
                            onRetryFailed = pdfViewModel::retryFailedPages,
                            onChangePageSizeMode = pdfViewModel::switchPageSizeMode,
                            onClose = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
