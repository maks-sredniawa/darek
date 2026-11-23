package app.darek

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.darek.ui.appViewModelExtras
import app.darek.ui.fuchy.CityScreen
import app.darek.ui.fuchy.FuchyScreen
import app.darek.ui.fuchy.GrantsScreen
import app.darek.ui.fuchy.InstallScreen
import app.darek.ui.fuchy.InstallViewModel
import app.darek.ui.fuchy.PageScreen
import app.darek.ui.home.HomeScreen
import app.darek.ui.settings.SettingsScreen
import app.darek.ui.support.LicenceTextScreen
import app.darek.ui.support.LicencesScreen
import app.darek.ui.support.SupportScreen
import app.darek.ui.support.TipSection

/**
 * What every back-stack entry is wrapped in — above all, **one `ViewModelStore` per entry**.
 *
 * Nav3 does not do this on its own. The ViewModel decorator ships in a separate artifact
 * (`lifecycle-viewmodel-navigation3`), which `navigation3-ui` does not depend on, so `NavDisplay`'s
 * default list cannot contain it. Without it every `viewModel()` resolves to the **Activity's**
 * store and outlives the screen that created it, and only killing the process clears it.
 *
 * `rememberSaveableStateHolderNavEntryDecorator` is Nav3's own default, restated because passing a list
 * replaces the defaults rather than adding to them.
 *
 * Extracted from [MainNavigation] so a test can assert the scoping directly.
 */
@Composable
internal fun appEntryDecorators(): List<NavEntryDecorator<NavKey>> =
    listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

/**
 * The app shell: a [NavDisplay] rooted at [Home]. Everything else is pushed from there, so Back
 * always walks towards Home and Back from Home exits.
 */
@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Home)
    val activity = LocalActivity.current

    Scaffold(modifier = modifier) { insets ->
        NavDisplay(
            backStack = backStack,
            // **The keyboard is an inset like any other, and this Scaffold owns them.**
            //
            // `MainActivity` sets `decorFitsSystemWindows = false` for edge-to-edge, which makes the
            // manifest's `adjustResize` inoperative from API 30 — the window manager downgrades it
            // to a *pan*. With nothing consuming `WindowInsets.ime`, the system then pans the whole
            // window to keep the focused field in view, which slides the top of a long form under
            // the status bar and pushes its top bar off the screen entirely.
            //
            // `consumeWindowInsets` before `imePadding` is what stops it double-counting: the
            // keyboard's inset is measured from the bottom of the *screen*, so it already contains
            // the navigation-bar height that `padding(insets)` just applied. Consuming says "that
            // part is handled", leaving `imePadding` to add only the rest.
            modifier = Modifier.padding(insets).consumeWindowInsets(insets).imePadding(),
            entryDecorators = appEntryDecorators(),
            onBack = {
                if (backStack.size > 1) backStack.removeLastOrNull() else activity?.finish()
            },
            // Kotlin note: this is a builder DSL, not a map literal — `entry<Home> { … }` registers
            // the composable that renders that key, and the lambda receives the key itself, which is
            // how a key carrying arguments passes them in.
            entryProvider =
                entryProvider {
                    entry<Home> {
                        HomeScreen(
                            onOpenSettings = { backStack.add(Settings) },
                            onOpenSupport = { backStack.add(Support) },
                            onOpenPage = { backStack.add(FuchyPageList) },
                        )
                    }
                    entry<Settings> {
                        SettingsScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenLicences = { backStack.add(Licences) },
                            onOpenSupport = { backStack.add(Support) },
                            onOpenFuchy = { backStack.add(FuchyList) },
                            onOpenGrants = { backStack.add(FuchyGrants) },
                            onOpenPage = { backStack.add(FuchyPageList) },
                        )
                    }
                    entry<FuchyList> {
                        FuchyScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onInstall = { backStack.add(FuchaInstall(it.toString())) },
                            onPickCity = { id, switchOn -> backStack.add(FuchaCity(id, switchOn)) },
                            onOpenPage = { backStack.add(FuchyPageList) },
                        )
                    }
                    entry<FuchyPageList> {
                        PageScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onInstall = { backStack.add(FuchaFromPage(it)) },
                        )
                    }
                    entry<FuchaFromPage> { key ->
                        InstallScreen(
                            viewModel(factory = InstallViewModel.factory(key.listing), extras = appViewModelExtras()),
                            onDone = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<FuchaCity> { key ->
                        CityScreen(
                            fuchaId = key.fuchaId,
                            switchOn = key.switchOn,
                            onDone = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<FuchyGrants> {
                        GrantsScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<FuchaInstall> { key ->
                        InstallScreen(
                            viewModel(
                                factory = InstallViewModel.factory(key.uri.toUri()),
                                extras = appViewModelExtras(),
                            ),
                            onDone = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Support> {
                        SupportScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenCredits = { backStack.add(Licences) },
                            tip = { TipSection() },
                        )
                    }
                    entry<Licences> {
                        LicencesScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenLicenceText = { spdxId, title -> backStack.add(LicenceText(spdxId, title)) },
                        )
                    }
                    entry<LicenceText> { key ->
                        LicenceTextScreen(
                            spdxId = key.spdxId,
                            title = key.title,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
        )
    }
}
