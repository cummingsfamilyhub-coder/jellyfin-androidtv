package org.jellyfin.androidtv.ui.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import org.jellyfin.androidtv.VesperServiceConfig
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.preference.PreferencesActivity
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject

class MobileSettingsActivity : FragmentActivity() {
    private val userRepository by inject<UserRepository>()
    private val serverRepository by inject<ServerRepository>()
    private val api by inject<ApiClient>()

    private val preferences by lazy { getSharedPreferences("vesper", MODE_PRIVATE) }

    private var showMiniPlayer by mutableStateOf(true)
    private var popularityScope by mutableStateOf("GLOBAL")
    private var tmdbApiKey by mutableStateOf("")
    private var seerrApiKey by mutableStateOf("")
    private var musicAssistantUrl by mutableStateOf("")
    private var musicAssistantToken by mutableStateOf("")
    private var pinConfigured by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPreferences()

        val currentUser = userRepository.currentUser.value
        val currentServer = serverRepository.currentServer.value
        val profileImageUrl = currentUser?.primaryImage?.getUrl(api)
        pinConfigured = if (currentUser != null && currentServer != null) {
            VesperProfilePinStore.hasPin(this, currentServer.id, currentUser.id)
        } else {
            false
        }
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val appVersion = "${packageInfo.versionName ?: "dev"} ($versionCode)"

        setContent {
            var page by remember { mutableStateOf(SettingsPage.MAIN) }

            BackHandler {
                if (page == SettingsPage.MAIN) finish()
                else page = SettingsPage.MAIN
            }

            VesperSettingsScreen(
                page = page,
                userName = currentUser?.name ?: "Vesper",
                userAvatarUrl = profileImageUrl,
                userId = currentUser?.id,
                serverId = currentServer?.id,
                pinConfigured = pinConfigured,
                appVersion = appVersion,
                jellyfinName = currentServer?.name ?: "Jellyfin",
                showMiniPlayer = showMiniPlayer,
                popularityScope = popularityScope,
                tmdbApiKey = tmdbApiKey,
                seerrApiKey = seerrApiKey,
                musicAssistantUrl = musicAssistantUrl,
                musicAssistantToken = musicAssistantToken,
                onBack = {
                    if (page == SettingsPage.MAIN) finish()
                    else page = SettingsPage.MAIN
                },
                onPage = { page = it },
                onMiniPlayer = { enabled ->
                    showMiniPlayer = enabled
                    preferences.edit()
                        .putBoolean("show_persistent_mini_player", enabled)
                        .apply()
                    markChanged()
                },
                onPopularity = { scope ->
                    popularityScope = scope
                    preferences.edit()
                        .putString("popularity_scope", scope)
                        .apply()
                    markChanged()
                },
                onSaveMusicAssistant = { url, token ->
                    musicAssistantUrl = url.trim().trimEnd('/')
                    musicAssistantToken = token.trim()
                    preferences.edit()
                        .putString("music_assistant_url", musicAssistantUrl)
                        .putString("music_assistant_token", musicAssistantToken)
                        .apply()
                    markChanged()
                    page = SettingsPage.MAIN
                },
                onSaveSeerr = { key ->
                    seerrApiKey = key.trim()
                    preferences.edit()
                        .remove("seerr_url")
                        .putString("seerr_api_key", seerrApiKey)
                        .apply()
                    markChanged()
                    page = SettingsPage.MAIN
                },
                onSaveTmdb = { key ->
                    tmdbApiKey = key.trim()
                    preferences.edit()
                        .putString("tmdb_api_key", tmdbApiKey)
                        .apply()
                    markChanged()
                    page = SettingsPage.MAIN
                },
                onSetPin = { pin ->
                    if (currentUser != null && currentServer != null) {
                        VesperProfilePinStore.setPin(this, currentServer.id, currentUser.id, pin)
                        pinConfigured = true
                        markChanged()
                    }
                },
                onRemovePin = {
                    if (currentUser != null && currentServer != null) {
                        VesperProfilePinStore.removePin(this, currentServer.id, currentUser.id)
                        pinConfigured = false
                        markChanged()
                    }
                },
                onOpenJellyfinSettings = {
                    startActivity(Intent(this, PreferencesActivity::class.java))
                },
            )
        }
    }

    private fun loadPreferences() {
        showMiniPlayer = preferences.getBoolean("show_persistent_mini_player", true)
        popularityScope = preferences.getString("popularity_scope", "GLOBAL") ?: "GLOBAL"
        tmdbApiKey = preferences.getString("tmdb_api_key", "").orEmpty()
        seerrApiKey = preferences.getString("seerr_api_key", "").orEmpty()
        musicAssistantUrl = preferences
            .getString("music_assistant_url", "http://192.168.1.34:8095")
            .orEmpty()
        musicAssistantToken = preferences.getString("music_assistant_token", "").orEmpty()
    }

    private fun markChanged() {
        setResult(RESULT_OK)
    }
}

private enum class SettingsPage {
    MAIN,
    PROFILE,
    JELLYFIN,
    MUSIC_ASSISTANT,
    SEERR,
    TMDB,
}

@Composable
private fun VesperSettingsScreen(
    page: SettingsPage,
    userName: String,
    userAvatarUrl: String?,
    userId: java.util.UUID?,
    serverId: java.util.UUID?,
    pinConfigured: Boolean,
    appVersion: String,
    jellyfinName: String,
    showMiniPlayer: Boolean,
    popularityScope: String,
    tmdbApiKey: String,
    seerrApiKey: String,
    musicAssistantUrl: String,
    musicAssistantToken: String,
    onBack: () -> Unit,
    onPage: (SettingsPage) -> Unit,
    onMiniPlayer: (Boolean) -> Unit,
    onPopularity: (String) -> Unit,
    onSaveMusicAssistant: (String, String) -> Unit,
    onSaveSeerr: (String) -> Unit,
    onSaveTmdb: (String) -> Unit,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit,
    onOpenJellyfinSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF090A11),
                        Color(0xFF05080C),
                    )
                )
            )
            .statusBarsPadding(),
    ) {
        when (page) {
            SettingsPage.MAIN -> SettingsHome(
                userName = userName,
                userAvatarUrl = userAvatarUrl,
                appVersion = appVersion,
                jellyfinName = jellyfinName,
                showMiniPlayer = showMiniPlayer,
                popularityScope = popularityScope,
                tmdbConfigured = tmdbApiKey.isNotBlank(),
                seerrConfigured = seerrApiKey.isNotBlank(),
                musicAssistantConfigured = musicAssistantUrl.isNotBlank() && musicAssistantToken.isNotBlank(),
                onBack = onBack,
                onPage = onPage,
                onMiniPlayer = onMiniPlayer,
                onPopularity = onPopularity,
                onOpenJellyfinSettings = onOpenJellyfinSettings,
            )

            SettingsPage.PROFILE -> ProfileSecurityPage(
                userName = userName,
                userAvatarUrl = userAvatarUrl,
                pinConfigured = pinConfigured,
                onBack = onBack,
                onSetPin = onSetPin,
                onRemovePin = onRemovePin,
            )

            SettingsPage.JELLYFIN -> JellyfinSettingsPage(
                jellyfinName = jellyfinName,
                onBack = onBack,
                onOpenJellyfinSettings = onOpenJellyfinSettings,
            )

            SettingsPage.MUSIC_ASSISTANT -> MusicAssistantSettingsPage(
                initialUrl = musicAssistantUrl,
                initialToken = musicAssistantToken,
                onBack = onBack,
                onSave = onSaveMusicAssistant,
            )

            SettingsPage.SEERR -> SeerrSettingsPage(
                initialKey = seerrApiKey,
                onBack = onBack,
                onSave = onSaveSeerr,
            )

            SettingsPage.TMDB -> TmdbSettingsPage(
                initialKey = tmdbApiKey,
                onBack = onBack,
                onSave = onSaveTmdb,
            )
        }
    }
}

@Composable
private fun SettingsHome(
    userName: String,
    userAvatarUrl: String?,
    appVersion: String,
    jellyfinName: String,
    showMiniPlayer: Boolean,
    popularityScope: String,
    tmdbConfigured: Boolean,
    seerrConfigured: Boolean,
    musicAssistantConfigured: Boolean,
    onBack: () -> Unit,
    onPage: (SettingsPage) -> Unit,
    onMiniPlayer: (Boolean) -> Unit,
    onPopularity: (String) -> Unit,
    onOpenJellyfinSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsTopBar(title = "Settings", onBack = onBack)
        }

        item {
            ProfileSettingsCard(
                userName = userName,
                userAvatarUrl = userAvatarUrl,
                onClick = { onPage(SettingsPage.PROFILE) },
            )
        }

        item { SettingsSectionLabel("PLAYBACK") }

        item {
            SettingsCard {
                SettingsToggleRow(
                    icon = "♫",
                    title = "Persistent mini-player",
                    subtitle = "Keep music controls above the navigation dock while audio is active.",
                    checked = showMiniPlayer,
                    onCheckedChange = onMiniPlayer,
                )
            }
        }

        item { SettingsSectionLabel("CONTENT & DISCOVERY") }

        item {
            SettingsCard {
                Column(Modifier.padding(15.dp)) {
                    BasicText(
                        "Popularity source",
                        style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        "Choose whether Vesper ranks content using global TMDb activity or your Jellyfin household.",
                        style = TextStyle(color = Color(0xFF8E97A4), fontSize = 12.sp, lineHeight = 17.sp),
                    )
                    Spacer(Modifier.height(13.dp))
                    SegmentedChoice(
                        selected = popularityScope,
                        left = "GLOBAL",
                        right = "LOCAL",
                        onSelect = onPopularity,
                    )
                }
            }
        }

        item { SettingsSectionLabel("CONNECTIONS") }

        item {
            SettingsCard {
                SettingsNavigationRow(
                    icon = "V",
                    title = "Jellyfin",
                    subtitle = jellyfinName,
                    status = "Connected",
                    onClick = { onPage(SettingsPage.JELLYFIN) },
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon = "♫",
                    title = "Music Assistant",
                    subtitle = "Music library, rooms and playback",
                    status = if (musicAssistantConfigured) "Connected" else "Needs setup",
                    onClick = { onPage(SettingsPage.MUSIC_ASSISTANT) },
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon = "S",
                    title = "Seerr",
                    subtitle = "Search and one-tap requests",
                    status = if (seerrConfigured) "Connected" else "Needs setup",
                    onClick = { onPage(SettingsPage.SEERR) },
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon = "T",
                    title = "TMDb",
                    subtitle = "Global popularity and metadata",
                    status = if (tmdbConfigured) "Configured" else "Needs setup",
                    onClick = { onPage(SettingsPage.TMDB) },
                )
            }
        }

        item { SettingsSectionLabel("ADVANCED") }

        item {
            SettingsCard {
                SettingsNavigationRow(
                    icon = "⚙",
                    title = "Jellyfin app settings",
                    subtitle = "Playback, client and legacy Jellyfin options",
                    status = null,
                    onClick = onOpenJellyfinSettings,
                )
            }
        }

        item {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    BasicText(
                        "Vesper",
                        style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        "Version $appVersion",
                        style = TextStyle(color = Color(0xFFAAA2C7), fontSize = 11.sp),
                    )
                    Spacer(Modifier.height(5.dp))
                    BasicText(
                        "Personal media, one place.",
                        style = TextStyle(color = Color(0xFF777F8C), fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSettingsCard(
    userName: String,
    userAvatarUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xD91A1B28))
            .border(1.dp, Color(0x445D4AA8), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VesperProfileAvatar(
            name = userName,
            imageUrl = userAvatarUrl,
            modifier = Modifier.size(54.dp),
        )

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            BasicText(
                userName,
                style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                "Profile & security",
                style = TextStyle(color = Color(0xFF969EAA), fontSize = 12.sp),
            )
        }

        BasicText(
            "›",
            style = TextStyle(color = Color(0xFF8C86A8), fontSize = 22.sp),
        )
    }
}

@Composable
private fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(Color(0x661D1F2D))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "‹",
                style = TextStyle(color = Color.White, fontSize = 28.sp),
            )
        }

        Spacer(Modifier.width(14.dp))

        BasicText(
            title,
            style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun SettingsSectionLabel(label: String) {
    BasicText(
        label,
        style = TextStyle(
            color = Color(0xFF8077A9),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
        ),
        modifier = Modifier.padding(start = 5.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xD9161824))
            .border(1.dp, Color(0x334D4A75), RoundedCornerShape(22.dp)),
    ) {
        content()
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: String,
    title: String,
    subtitle: String,
    status: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF232239)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                icon,
                style = TextStyle(color = Color(0xFFB9A8FF), fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            BasicText(
                title,
                style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                subtitle,
                style = TextStyle(color = Color(0xFF858E9A), fontSize = 11.sp),
                maxLines = 1,
            )
        }

        if (!status.isNullOrBlank()) {
            BasicText(
                status,
                style = TextStyle(
                    color = if (status == "Needs setup") Color(0xFFE1B778) else Color(0xFFA8D9C1),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.width(8.dp))
        }

        BasicText(
            "›",
            style = TextStyle(color = Color(0xFF777F8C), fontSize = 22.sp),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF232239)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                icon,
                style = TextStyle(color = Color(0xFFB9A8FF), fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            BasicText(
                title,
                style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                subtitle,
                style = TextStyle(color = Color(0xFF858E9A), fontSize = 11.sp, lineHeight = 15.sp),
            )
        }

        Spacer(Modifier.width(12.dp))
        VesperSwitch(checked = checked)
    }
}

@Composable
private fun VesperSwitch(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) Color(0xFF6E55E8) else Color(0xFF353946))
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White),
        )
    }
}

@Composable
private fun SegmentedChoice(
    selected: String,
    left: String,
    right: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0D1118))
            .padding(4.dp),
    ) {
        listOf(left, right).forEach { value ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (selected == value) Color(0xFF352D67) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    value.lowercase().replaceFirstChar { it.uppercase() },
                    style = TextStyle(
                        color = if (selected == value) Color.White else Color(0xFF8D95A1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 69.dp)
            .height(1.dp)
            .background(Color(0x1FFFFFFF)),
    )
}

@Composable
private fun ProfileSecurityPage(
    userName: String,
    userAvatarUrl: String?,
    pinConfigured: Boolean,
    onBack: () -> Unit,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit,
) {
    SettingsDetailScaffold(
        title = "Profile & Security",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VesperProfileAvatar(
                name = userName,
                imageUrl = userAvatarUrl,
                modifier = Modifier.size(88.dp),
            )
            Spacer(Modifier.height(12.dp))
            BasicText(
                userName,
                style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(5.dp))
            BasicText(
                if (userAvatarUrl.isNullOrBlank()) "Using Vesper initials until a Jellyfin profile picture is added."
                else "Using your Jellyfin profile picture.",
                style = TextStyle(color = Color(0xFF858E9A), fontSize = 11.sp),
            )
        }

        Spacer(Modifier.height(22.dp))

        PinSettingsCard(
            pinConfigured = pinConfigured,
            onSetPin = onSetPin,
            onRemovePin = onRemovePin,
        )

        Spacer(Modifier.height(14.dp))

        SettingsInfoCard(
            title = "Jellyfin password",
            value = "Still your real account credential",
            helper = "The optional Vesper PIN only unlocks this saved profile on this device. It never replaces your Jellyfin password.",
        )
    }
}

private enum class PinSetupStage {
    IDLE,
    NEW,
    CONFIRM,
}

@Composable
private fun PinSettingsCard(
    pinConfigured: Boolean,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit,
) {
    var stage by remember { mutableStateOf(PinSetupStage.IDLE) }
    var firstPin by remember { mutableStateOf("") }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        "Vesper PIN",
                        style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(3.dp))
                    BasicText(
                        if (pinConfigured) "4-digit PIN enabled on this device"
                        else "Optional 4-digit local profile lock",
                        style = TextStyle(color = Color(0xFF858E9A), fontSize = 11.sp),
                    )
                }
                BasicText(
                    if (pinConfigured) "On" else "Off",
                    style = TextStyle(
                        color = if (pinConfigured) Color(0xFFA8D9C1) else Color(0xFF858E9A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }

            when (stage) {
                PinSetupStage.IDLE -> {
                    Spacer(Modifier.height(14.dp))
                    SettingsPrimaryButton(
                        label = if (pinConfigured) "Change PIN" else "Set PIN",
                        onClick = {
                            firstPin = ""
                            entry = ""
                            error = null
                            stage = PinSetupStage.NEW
                        },
                    )
                    if (pinConfigured) {
                        Spacer(Modifier.height(9.dp))
                        SettingsSecondaryButton(
                            label = "Remove PIN",
                            onClick = onRemovePin,
                        )
                    }
                }

                PinSetupStage.NEW, PinSetupStage.CONFIRM -> {
                    Spacer(Modifier.height(16.dp))
                    BasicText(
                        if (stage == PinSetupStage.NEW) "Choose a 4-digit PIN"
                        else "Enter it again to confirm",
                        style = TextStyle(color = Color(0xFFDDE4EC), fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(12.dp))
                    PinDots(length = entry.length)
                    Spacer(Modifier.height(14.dp))
                    VesperPinPad(
                        onDigit = { digit ->
                            if (entry.length < 4) {
                                val next = entry + digit
                                entry = next
                                if (next.length == 4) {
                                    if (stage == PinSetupStage.NEW) {
                                        firstPin = next
                                        entry = ""
                                        error = null
                                        stage = PinSetupStage.CONFIRM
                                    } else if (next == firstPin) {
                                        onSetPin(next)
                                        firstPin = ""
                                        entry = ""
                                        error = null
                                        stage = PinSetupStage.IDLE
                                    } else {
                                        firstPin = ""
                                        entry = ""
                                        error = "Those PINs didn't match. Try again."
                                        stage = PinSetupStage.NEW
                                    }
                                }
                            }
                        },
                        onBackspace = {
                            if (entry.isNotEmpty()) entry = entry.dropLast(1)
                        },
                    )
                    if (!error.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        BasicText(
                            error.orEmpty(),
                            style = TextStyle(color = Color(0xFFFFB7BE), fontSize = 11.sp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SettingsSecondaryButton(
                        label = "Cancel",
                        onClick = {
                            firstPin = ""
                            entry = ""
                            error = null
                            stage = PinSetupStage.IDLE
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinDots(length: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (index < length) Color(0xFF9B83FF)
                        else Color(0xFF303543)
                    ),
            )
        }
    }
}

@Composable
internal fun VesperPinPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { key ->
                    if (key.isBlank()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF11151E))
                                .clickable {
                                    if (key == "⌫") onBackspace()
                                    else onDigit(key)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                key,
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = if (key == "⌫") 18.sp else 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSecondaryButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF242936))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(color = Color(0xFFD6DCE5), fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun JellyfinSettingsPage(
    jellyfinName: String,
    onBack: () -> Unit,
    onOpenJellyfinSettings: () -> Unit,
) {
    SettingsDetailScaffold(
        title = "Jellyfin",
        onBack = onBack,
    ) {
        SettingsInfoCard(
            title = jellyfinName,
            value = VesperServiceConfig.JELLYFIN_BASE_URL,
            helper = "Vesper uses this canonical HTTPS endpoint on Wi-Fi and mobile data.",
        )
        Spacer(Modifier.height(16.dp))
        SettingsPrimaryButton(
            label = "Open Jellyfin app settings",
            onClick = onOpenJellyfinSettings,
        )
    }
}

@Composable
private fun MusicAssistantSettingsPage(
    initialUrl: String,
    initialToken: String,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var token by remember(initialToken) { mutableStateOf(initialToken) }

    SettingsDetailScaffold(
        title = "Music Assistant",
        onBack = onBack,
    ) {
        SettingsField(
            label = "Server URL",
            value = url,
            onValueChange = { url = it },
            placeholder = "http://192.168.1.34:8095",
        )
        Spacer(Modifier.height(14.dp))
        SettingsField(
            label = "Access token",
            value = token,
            onValueChange = { token = it },
            placeholder = "Music Assistant token",
            password = true,
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            "Used for your music library, room control and playback.",
            style = TextStyle(color = Color(0xFF858E9A), fontSize = 11.sp, lineHeight = 16.sp),
        )
        Spacer(Modifier.height(20.dp))
        SettingsPrimaryButton(
            label = "Save connection",
            onClick = { onSave(url, token) },
            enabled = url.isNotBlank() && token.isNotBlank(),
        )
    }
}

@Composable
private fun SeerrSettingsPage(
    initialKey: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    var key by remember(initialKey) { mutableStateOf(initialKey) }

    SettingsDetailScaffold(
        title = "Seerr",
        onBack = onBack,
    ) {
        SettingsInfoCard(
            title = "Service",
            value = VesperServiceConfig.SEERR_BASE_URL,
            helper = "Search and one-tap requests use Vesper's canonical remote endpoint.",
        )
        Spacer(Modifier.height(16.dp))
        SettingsField(
            label = "API key",
            value = key,
            onValueChange = { key = it },
            placeholder = "Seerr API key",
            password = true,
        )
        Spacer(Modifier.height(20.dp))
        SettingsPrimaryButton(
            label = "Save connection",
            onClick = { onSave(key) },
            enabled = key.isNotBlank(),
        )
    }
}

@Composable
private fun TmdbSettingsPage(
    initialKey: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    var key by remember(initialKey) { mutableStateOf(initialKey) }

    SettingsDetailScaffold(
        title = "TMDb",
        onBack = onBack,
    ) {
        SettingsField(
            label = "TMDb v3 API key",
            value = key,
            onValueChange = { key = it },
            placeholder = "TMDb API key",
            password = true,
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            "Used for global popularity and discovery ranking.",
            style = TextStyle(color = Color(0xFF858E9A), fontSize = 11.sp, lineHeight = 16.sp),
        )
        Spacer(Modifier.height(20.dp))
        SettingsPrimaryButton(
            label = "Save key",
            onClick = { onSave(key) },
            enabled = key.isNotBlank(),
        )
    }
}

@Composable
private fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
    ) {
        item {
            SettingsTopBar(title = title, onBack = onBack)
        }
        item {
            Column(Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsInfoCard(
    title: String,
    value: String,
    helper: String,
) {
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            BasicText(
                title,
                style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(5.dp))
            BasicText(
                value,
                style = TextStyle(color = Color(0xFFB1A4EE), fontSize = 12.sp),
            )
            Spacer(Modifier.height(7.dp))
            BasicText(
                helper,
                style = TextStyle(color = Color(0xFF858E9A), fontSize = 11.sp, lineHeight = 16.sp),
            )
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
) {
    Column {
        BasicText(
            label,
            style = TextStyle(color = Color(0xFFDDE4EC), fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF11151E))
                .border(1.dp, Color(0x334D4A75), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            if (value.isEmpty()) {
                BasicText(
                    placeholder,
                    style = TextStyle(color = Color(0xFF5F6875), fontSize = 14.sp),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color(0xFFB6A4FF)),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun SettingsPrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Color(0xFFE9F3FA) else Color(0xFF343B45))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (enabled) Color(0xFF071017) else Color(0xFF7D8790),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
