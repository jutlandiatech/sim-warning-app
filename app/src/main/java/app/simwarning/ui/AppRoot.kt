package app.simwarning.ui

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.simwarning.R
import app.simwarning.core.*
import app.simwarning.platform.*
import app.simwarning.setup.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.os.SystemClock
import android.text.format.DateUtils
import kotlinx.coroutines.delay

private val Blue = Color(0xFF2769AD)
private enum class Page(val textKey: String, val rootId: String) {
    MAIN("app.name", AppIds.MAIN), SETTINGS("settings.title", AppIds.SETTINGS),
    LANGUAGE("settings.language", AppIds.LANGUAGE_SCREEN), INTERVAL("settings.interval", AppIds.INTERVAL_SCREEN),
    PREVIEW("settings.preview", AppIds.PREVIEW_SCREEN), TEST("settings.test", AppIds.TEST_SCREEN),
    PERMISSIONS("settings.permissions", AppIds.PERMISSION_SCREEN), HELP("settings.help", AppIds.HELP_SCREEN),
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(refreshToken: Int, openMainToken: Int, review: () -> Unit, openSettings: () -> Unit, notifications: NotificationCenter) {
    val context = LocalContext.current
    val store = remember(context) { AppPreferences(context) }
    val platform = remember(context) { AndroidMonitorPlatform(context) }
    val controller = remember(store, platform) { AppController(store, platform) }
    var settings by remember { mutableStateOf(store.read()) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> settings = store.read() }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    if (!settings.setupComplete) {
        SetupFlow(settings.language.code, { if (AndroidAccess(context).ready()) SetupReadiness.READY else SetupReadiness.ACCESS_REQUIRED },
            review, { runCatching { controller.finishSetup(it) }.getOrDefault(false) }, refreshToken)
        return
    }
    val catalog = remember(settings.language) { TextCatalog.load(context, settings.language) }
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    var warningKind by rememberSaveable { mutableStateOf(WarningKind.CONNECTION_LOSS) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var failedStart by remember { mutableStateOf(false) }
    LaunchedEffect(openMainToken) { if (openMainToken > 0) page = Page.MAIN }
    LaunchedEffect(page, warningKind) { feedback = null; notifications.stopPreview() }
    fun back() { page = if (page == Page.SETTINGS) Page.MAIN else Page.SETTINGS }
    BackHandler(page != Page.MAIN) { back() }
    val access = AndroidAccess(context)
    // Re-read on Android-result/lifecycle refresh even if no preference changed.
    @Suppress("UNUSED_VARIABLE") val actualRefresh = refreshToken
    val ready = access.ready()
    val outageSince = MonitorRuntime.outageSinceElapsedMs
    val owner = LocalLifecycleOwner.current
    val elapsedNow by produceState(SystemClock.elapsedRealtime(), outageSince, page, owner) {
        if (outageSince != null && page == Page.MAIN) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { value = SystemClock.elapsedRealtime(); delay(1000) }
        }
    }
    val body = TextStyle(fontSize = 18.sp, lineHeight = 27.sp, localeList = LocaleList(settings.language.code))
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White, surface = Color.White, background = Color.White,
        onSurface = Color(0xFF15191E), onBackground = Color(0xFF15191E)),
        typography = Typography(bodyLarge = body, bodyMedium = body, labelLarge = body.copy(fontWeight = FontWeight.Medium))) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            key(page) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().testTag(page.rootId).semantics { testTagsAsResourceId = true }
                    .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (page != Page.MAIN) {
                        TextButton(onClick = { back() }, modifier = Modifier.heightIn(min = 48.dp).testTag(AppIds.BACK)) { Text(catalog.text("action.back")) }
                    } else {
                        Image(painterResource(R.drawable.ic_sim_warning), null, Modifier.size(48.dp))
                    }
                    Text(catalog.text(page.textKey), style = body.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth().testTag(if (page == Page.MAIN) AppIds.APP_NAME else AppIds.TITLE).semantics { heading() })
                    when (page) {
                        Page.MAIN -> {
                            val statusKey = when {
                                !settings.monitoringEnabled -> "status.off"
                                !ready -> "status.permission"
                                MonitorRuntime.starting -> "status.initial"
                                !MonitorRuntime.running -> "status.stopped"
                                else -> when (MonitorRuntime.status) {
                                    MonitorStatus.OFF -> "status.off"
                                    MonitorStatus.NEEDS_PERMISSION -> "status.permission"
                                    MonitorStatus.INTENTIONALLY_DISCONNECTED -> "status.intentional"
                                    MonitorStatus.INITIAL_GRACE -> "status.initial"
                                    MonitorStatus.CONNECTED -> "status.connected"
                                    MonitorStatus.LOSS_GRACE -> "status.lossGrace"
                                    MonitorStatus.OUTAGE -> "status.outage"
                                    MonitorStatus.RECOVERING -> "status.recovering"
                                    MonitorStatus.STATUS_UNAVAILABLE -> "status.unknown"
                                }
                            }
                            Text(catalog.text(statusKey), modifier = Modifier.testTag(AppIds.STATUS).semantics { liveRegion = LiveRegionMode.Polite })
                            if (outageSince != null) Text(catalog.text("warning.duration") + ": " + DateUtils.formatElapsedTime(((elapsedNow - outageSince) / 1000).coerceAtLeast(0)), Modifier.testTag(AppIds.DURATION))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(catalog.text("main.monitoring"), modifier = Modifier.weight(1f))
                                Switch(settings.monitoringEnabled, onCheckedChange = { enabled -> failedStart = !controller.setMonitoring(enabled) },
                                    modifier = Modifier.testTag(AppIds.SWITCH).semantics { contentDescription = catalog.text("main.monitoring") })
                            }
                            if (!ready) Action(catalog.text("setup.permissions.review"), AppIds.MAIN_REVIEW, review)
                            if (failedStart) Text(catalog.text("setup.permissions.startFailed"))
                            Action(catalog.text("main.settings"), AppIds.SETTINGS_BUTTON, { page = Page.SETTINGS })
                            Text(catalog.text("main.testVersion"))
                        }
                        Page.SETTINGS -> {
                            val menu = listOf(
                                Triple(Page.LANGUAGE, "language", R.drawable.ic_language), Triple(Page.INTERVAL, "warning_interval", R.drawable.ic_interval),
                                Triple(Page.PREVIEW, "preview_sound", R.drawable.ic_sound), Triple(Page.TEST, "test_warning", R.drawable.ic_bell),
                                Triple(Page.PERMISSIONS, "permissions", R.drawable.ic_access), Triple(Page.HELP, "help", R.drawable.ic_help))
                            for ((destination, name, icon) in menu) {
                                OutlinedButton(onClick = { page = destination }, shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(AppIds.menu(name))) {
                                    Icon(painterResource(icon), null, Modifier.size(24.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(catalog.text(destination.textKey))
                                        if (destination == Page.LANGUAGE) Text(catalog.text("language.${settings.language.code}"), modifier = Modifier.testTag(AppIds.LANGUAGE_VALUE))
                                        if (destination == Page.INTERVAL) Text(catalog.text("setup.interval.value", "minutes" to settings.intervalMinutes), modifier = Modifier.testTag(AppIds.INTERVAL_VALUE))
                                    }
                                    Text("›", modifier = Modifier.clearAndSetSemantics {})
                                }
                            }
                        }
                        Page.LANGUAGE -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            for ((language, flag) in listOf(AppLanguage.DANISH to "🇩🇰", AppLanguage.ENGLISH to "🇬🇧", AppLanguage.GERMAN to "🇩🇪")) {
                                Choice(catalog.text("language.${language.code}"), settings.language == language, AppIds.language(language.code), flag) { controller.setLanguage(language) }
                            }
                        }
                        Page.INTERVAL -> {
                            Text(catalog.text("setup.interval.body"))
                            Text(catalog.text("setup.interval.label"))
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(AppIds.INTERVAL_SELECTOR)) {
                                    Text(catalog.text("setup.interval.value", "minutes" to settings.intervalMinutes), Modifier.weight(1f))
                                    Icon(painterResource(R.drawable.ic_expand_more), null)
                                }
                                DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }) {
                                    for (minutes in listOf(2,4,6,8,10)) DropdownMenuItem(
                                        text = { Text(catalog.text("setup.interval.value", "minutes" to minutes)) },
                                        onClick = { controller.setInterval(minutes); expanded = false },
                                        modifier = Modifier.heightIn(min = 56.dp).testTag(AppIds.interval(minutes)).semantics { selected = minutes == settings.intervalMinutes })
                                }
                            }
                            Text(catalog.text("setup.visual.body"))
                        }
                        Page.PREVIEW, Page.TEST -> {
                            Column(Modifier.testTag(AppIds.TYPE_SELECTOR).selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Choice(catalog.text("warning.loss.title"), warningKind == WarningKind.CONNECTION_LOSS, AppIds.TYPE_LOSS) { warningKind = WarningKind.CONNECTION_LOSS }
                                Choice(catalog.text("warning.unknown.title"), warningKind == WarningKind.STATUS_UNAVAILABLE, AppIds.TYPE_UNKNOWN) { warningKind = WarningKind.STATUS_UNAVAILABLE }
                            }
                            if (page == Page.PREVIEW) Action(catalog.text("action.play"), AppIds.PLAY, {
                                feedback = if (notifications.playPreview(warningKind)) "feedback.played" else "feedback.suppressed"
                            }) else Action(catalog.text("action.test"), AppIds.TEST, {
                                feedback = if (notifications.sample(warningKind)) "feedback.posted" else "feedback.noAccess"
                            })
                            feedback?.let { Text(catalog.text(it), Modifier.testTag(if (page == Page.PREVIEW) AppIds.PREVIEW_FEEDBACK else AppIds.TEST_FEEDBACK).semantics { liveRegion = LiveRegionMode.Polite }) }
                        }
                        Page.PERMISSIONS -> {
                            Text(catalog.text("permissions.explanation"), Modifier.testTag(AppIds.PERMISSION_EXPLANATION))
                            Text(catalog.text(if (ready) "setup.permissions.ready" else "setup.permissions.required"), Modifier.testTag(AppIds.PERMISSION_STATUS))
                            Text("${catalog.text("permissions.phone")}: ${catalog.text(if (access.phoneAllowed()) "permissions.allowed" else "permissions.needed")}", Modifier.testTag(AppIds.PHONE_ACCESS))
                            Text("${catalog.text("permissions.notifications")}: ${catalog.text(if (access.notificationsAllowed()) "permissions.allowed" else "permissions.needed")}", Modifier.testTag(AppIds.NOTIFICATION_ACCESS))
                            Action(catalog.text("setup.permissions.review"), AppIds.REVIEW, review)
                            Action(catalog.text("permissions.openSettings"), AppIds.OPEN_SYSTEM_SETTINGS, openSettings)
                        }
                        Page.HELP -> Column(Modifier.testTag(AppIds.HELP_CONTENT), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            for (section in listOf("purpose", "timing", "unknown", "control", "repair", "background", "access")) {
                                Column(Modifier.testTag(AppIds.help(section)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(catalog.text("help.$section.title"), style = body.copy(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp), modifier = Modifier.semantics { heading() })
                                    Text(catalog.text("help.$section.body"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Action(text: String, tag: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag), shape = RoundedCornerShape(12.dp)) { Text(text) }
}
@Composable private fun Choice(text: String, selected: Boolean, tag: String, flag: String? = null, onSelect: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (selected) Blue else Color(0xFFC2CEDB)), color = if (selected) Color(0xFFEDF4FC) else Color.White) {
        Row(Modifier.fillMaxWidth().testTag(tag).selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RadioButton(selected, onClick = null)
            flag?.let { Text(it, modifier = Modifier.clearAndSetSemantics {}) }
            Text(text, Modifier.weight(1f))
        }
    }
}
