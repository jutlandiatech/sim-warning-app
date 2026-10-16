package app.simwarning.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.simwarning.R
import app.simwarning.core.AppLanguage
import app.simwarning.core.SetupState
import app.simwarning.core.SetupStep

enum class SetupReadiness { ACCESS_REQUIRED, READY, UNAVAILABLE }

private val Blue = Color(0xFF2769AD)
private val Ink = Color(0xFF15191E)
private val SecondaryInk = Color(0xFF454E58)
private val SetupSaver = listSaver<SetupState, Any>(
    save = { listOf(it.language.name, it.intervalMinutes, it.step.name) },
    restore = { SetupState(AppLanguage.valueOf(it[0] as String), it[1] as Int, SetupStep.valueOf(it[2] as String)) },
)

/** Pure setup presentation. The lifecycle owner supplies real readiness and atomic start/persistence. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SetupFlow(
    deviceLanguage: String,
    readReadiness: () -> SetupReadiness,
    onReviewPermissions: () -> Unit,
    onStartMonitoring: (SetupState) -> Boolean,
    refreshToken: Int = 0,
) {
    var state by rememberSaveable(stateSaver = SetupSaver) {
        mutableStateOf(SetupState(language = AppLanguage.forDevice(deviceLanguage)))
    }
    val context = LocalContext.current
    val catalog = remember(state.language, context) { TextCatalog.load(context, state.language) }
    var readiness by remember { mutableStateOf(readReadiness()) }
    var completed by rememberSaveable { mutableStateOf(false) }
    var startFailed by rememberSaveable { mutableStateOf(false) }
    val currentReadiness by rememberUpdatedState(readReadiness)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) readiness = currentReadiness()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.step, refreshToken) { readiness = currentReadiness() }
    BackHandler(enabled = state.step != SetupStep.LANGUAGE && !completed) { state = state.back() }

    // Leave color to the component's content color (notably white on primary buttons).
    val body = TextStyle(fontSize = 18.sp, lineHeight = 27.sp, localeList = LocaleList(state.language.code))
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White, background = Color.White,
            surface = Color.White, onSurface = Ink, onBackground = Ink),
        typography = Typography(bodyLarge = body, bodyMedium = body, labelLarge = body.copy(fontWeight = FontWeight.Medium)),
    ) {
        Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()
            .testTag(ElementIds.SETUP_SCREEN).semantics { testTagsAsResourceId = true }) {
            key(state.step) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(R.drawable.ic_sim_warning), contentDescription = null, modifier = Modifier.size(36.dp))
                        Text(catalog.text("app.name"), style = body.copy(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, color = Ink),
                            modifier = Modifier.weight(1f).testTag(ElementIds.SETUP_APP_NAME))
                    }
                    val progress = state.step.ordinal + 1
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(catalog.text("setup.progress", "current" to progress, "total" to 4),
                            style = body.copy(fontSize = 16.sp, color = SecondaryInk),
                            modifier = Modifier.testTag(ElementIds.SETUP_STEP_PROGRESS).semantics {
                                progressBarRangeInfo = ProgressBarRangeInfo(progress.toFloat(), 1f..4f, 2)
                            })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clearAndSetSemantics {}) {
                            repeat(4) { index -> Box(Modifier.size(26.dp, 4.dp)
                                .background(if (index < progress) Blue else Color(0xFFD8E2ED), RoundedCornerShape(2.dp))) }
                        }
                    }
                    when (state.step) {
                        SetupStep.LANGUAGE -> Column(Modifier.testTag(ElementIds.SETUP_LANGUAGE_SCREEN), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Heading(catalog.text("setup.language.title"), ElementIds.SETUP_LANGUAGE_TITLE)
                            Copy(catalog.text("setup.language.hint"), ElementIds.SETUP_LANGUAGE_HINT)
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val options = listOf(
                                    Triple(AppLanguage.DANISH, "🇩🇰", catalog.text("language.da")),
                                    Triple(AppLanguage.ENGLISH, "🇬🇧", catalog.text("language.en")),
                                    Triple(AppLanguage.GERMAN, "🇩🇪", catalog.text("language.de")),
                                )
                                options.forEach { (language, flag, name) ->
                                    val selected = state.language == language
                                    val tag = when (language) {
                                        AppLanguage.DANISH -> ElementIds.SETUP_LANGUAGE_DA
                                        AppLanguage.ENGLISH -> ElementIds.SETUP_LANGUAGE_EN
                                        AppLanguage.GERMAN -> ElementIds.SETUP_LANGUAGE_DE
                                    }
                                    Surface(shape = RoundedCornerShape(12.dp), color = if (selected) Color(0xFFEDF4FC) else Color.White,
                                        border = BorderStroke(1.dp, if (selected) Blue else Color(0xFFC2CEDB))) {
                                        Row(Modifier.fillMaxWidth().testTag(tag)
                                            .selectable(selected, role = Role.RadioButton, onClick = { state = state.copy(language = language) })
                                            .defaultMinSize(minHeight = 64.dp).padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            RadioButton(selected, onClick = null)
                                            Text(flag, modifier = Modifier.clearAndSetSemantics {})
                                            Text(name)
                                        }
                                    }
                                }
                            }
                        }
                        SetupStep.EXPLANATION -> Column(Modifier.testTag(ElementIds.SETUP_EXPLANATION_SCREEN), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Heading(catalog.text("setup.explanation.title"), ElementIds.SETUP_EXPLANATION_TITLE)
                            Copy(catalog.text("setup.explanation.body"), ElementIds.SETUP_EXPLANATION_BODY)
                            Info(catalog, "setup.intentional", ElementIds.SETUP_INTENTIONAL_DISCONNECT_INFO)
                            Info(catalog, "setup.sound", ElementIds.SETUP_SOUND_RULES_INFO)
                        }
                        SetupStep.INTERVAL -> Column(Modifier.testTag(ElementIds.SETUP_INTERVAL_SCREEN), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Heading(catalog.text("setup.interval.title"), ElementIds.SETUP_INTERVAL_TITLE)
                            Copy(catalog.text("setup.interval.body"))
                            IntervalSelector(catalog, state.intervalMinutes) { state = state.copy(intervalMinutes = it) }
                            Info(catalog, "setup.visual", ElementIds.SETUP_VISUAL_WARNING_INFO)
                        }
                        SetupStep.PERMISSIONS -> Column(Modifier.testTag(ElementIds.SETUP_PERMISSIONS_SCREEN), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Heading(catalog.text("setup.permissions.title"), ElementIds.SETUP_PERMISSIONS_TITLE)
                            Copy(catalog.text("setup.permissions.body"), ElementIds.SETUP_PERMISSIONS_EXPLANATION)
                            Copy(catalog.text("setup.privacy"), ElementIds.SETUP_PRIVACY_INFO)
                            Copy(catalog.text("permissions.explanation"))
                            Copy(catalog.text("main.testVersion"))
                            OutlinedButton(onClick = {
                                onReviewPermissions()
                                readiness = readReadiness()
                                startFailed = false
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(ElementIds.SETUP_REVIEW_PERMISSIONS_BUTTON),
                                shape = RoundedCornerShape(12.dp)) { Text(catalog.text("setup.permissions.review")) }
                            val statusKey = when {
                                startFailed -> "setup.permissions.startFailed"
                                readiness == SetupReadiness.READY -> "setup.permissions.ready"
                                readiness == SetupReadiness.ACCESS_REQUIRED -> "setup.permissions.required"
                                else -> "setup.permissions.unavailable"
                            }
                            Text(catalog.text(statusKey), style = body.copy(color = SecondaryInk),
                                modifier = Modifier.testTag(ElementIds.SETUP_PERMISSION_STATUS).semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.step == SetupStep.PERMISSIONS) {
                            Button(onClick = {
                                readiness = readReadiness()
                                if (readiness == SetupReadiness.READY && !completed) {
                                    // Lock before invoking the coordinator to prevent re-entrant/double starts.
                                    completed = true
                                    startFailed = !onStartMonitoring(state)
                                    if (startFailed) completed = false
                                }
                            }, enabled = readiness == SetupReadiness.READY && !completed,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(ElementIds.SETUP_START_MONITORING_BUTTON),
                                shape = RoundedCornerShape(12.dp)) { Text(catalog.text("action.startMonitoring")) }
                        } else {
                            Button(onClick = { state = state.next() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .testTag(ElementIds.SETUP_CONTINUE_BUTTON), shape = RoundedCornerShape(12.dp)) { Text(catalog.text("action.continue")) }
                        }
                        if (state.step != SetupStep.LANGUAGE) {
                            TextButton(onClick = { state = state.back() }, enabled = !completed,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(ElementIds.SETUP_BACK_BUTTON)) { Text(catalog.text("action.back")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Heading(text: String, tag: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp, lineHeight = 35.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier.testTag(tag).semantics { heading() })
}

@Composable private fun Copy(text: String, tag: String? = null) {
    Text(text, style = MaterialTheme.typography.bodyLarge.copy(color = SecondaryInk),
        modifier = if (tag == null) Modifier else Modifier.testTag(tag))
}

@Composable private fun Info(catalog: TextCatalog, prefix: String, tag: String) {
    Column(Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = Color(0xFFE0E6ED))
        Text(catalog.text("$prefix.title"), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.semantics { heading() })
        Copy(catalog.text("$prefix.body"))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable private fun IntervalSelector(catalog: TextCatalog, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(catalog.text("setup.interval.label"))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .testTag(ElementIds.SETUP_INTERVAL_SELECTOR), shape = RoundedCornerShape(12.dp)) {
                Text(catalog.text("setup.interval.value", "minutes" to selected), modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(24.dp))
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }) {
                val options = listOf(2 to ElementIds.SETUP_INTERVAL_OPTION_2, 4 to ElementIds.SETUP_INTERVAL_OPTION_4,
                    6 to ElementIds.SETUP_INTERVAL_OPTION_6, 8 to ElementIds.SETUP_INTERVAL_OPTION_8, 10 to ElementIds.SETUP_INTERVAL_OPTION_10)
                options.forEach { (minutes, tag) ->
                    DropdownMenuItem(text = { Text(catalog.text("setup.interval.value", "minutes" to minutes)) },
                        onClick = { onSelect(minutes); expanded = false },
                        modifier = Modifier.testTag(tag).heightIn(min = 56.dp).semantics { this.selected = minutes == selected })
                }
            }
        }
    }
}
