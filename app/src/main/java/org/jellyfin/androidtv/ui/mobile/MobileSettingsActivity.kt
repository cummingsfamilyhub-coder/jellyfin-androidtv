package org.jellyfin.androidtv.ui.mobile

import android.content.Intent
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
import org.koin.android.ext.android.inject

class MobileSettingsActivity : FragmentActivity() {
    private val userRepository by inject<UserRepository>()
    private val serverRepository by inject<ServerRepository>()

    private val preferences by lazy { getSharedPreferences("vesper", MODE_PRIVATE) }

    private var showMiniPlayer by mutableStateOf(true)
    private var popularityScope by mutableStateOf("GLOBAL")
    private var tmdbApiKey by mutableStateOf("")
    private var seerrApiKey by mutableStateOf("")
    private var musicAssistantUrl by mutableStateOf("")
    private var musicAssistantToken by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPreferences()

        setContent {
            var page by remember { mutableStateOf(SettingsPage.MAIN) }

            BackHandler {
                if (page == SettingsPage.MAIN) finish()
                else page = SettingsPage.MAIN
            }

            VesperSettingsScreen(
                page = page,
                userName = userRepository.currentUser.value?.name ?: "Vesper",
                jellyfinName = serverRepository.currentServer.value?.name ?: "Jellyfin",
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
    JELLYFIN,
    MUSIC_ASSISTANT,
    SEERR,
    TMDB,
}

@Composable
private fun VesperSettingsScreen(
    page: SettingsPage,
    userName: String,
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
            ProfileSettingsCard(userName = userName)
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
    }
}

@Composable
private fun ProfileSettingsCard(userName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xD91A1B28))
            .border(1.dp, Color(0x445D4AA8), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3E2C79), Color(0xFF1B3457))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                userName.take(1).uppercase(),
                style = TextStyle(color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold),
            )
        }

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
            "PIN & avatar next",
            style = TextStyle(color = Color(0xFF9C8AE6), fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
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
    Box(
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
