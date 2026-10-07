package org.jellyfin.androidtv.ui.mobile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.lifecycleScope
import org.jellyfin.androidtv.VesperServiceConfig
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.preference.PreferencesActivity
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.FileInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.android.ext.android.inject
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileSettingsActivity : FragmentActivity() {
    private val userRepository by inject<UserRepository>()
    private val serverRepository by inject<ServerRepository>()
    private val api by inject<ApiClient>()

    private val preferences by lazy { getSharedPreferences("vesper", MODE_PRIVATE) }

    private var showMiniPlayer by mutableStateOf(true)
    private var popularityScope by mutableStateOf("GLOBAL")
    private var tmdbApiKey by mutableStateOf("")
    private var tvdbApiKey by mutableStateOf("")
    private var tvdbSubscriberPin by mutableStateOf("")
    private var tvdbConnectionVerified by mutableStateOf(false)
    private var tvdbConnectionMessage by mutableStateOf<String?>(null)
    private var tvdbConnectionTesting by mutableStateOf(false)
    private var seerrApiKey by mutableStateOf("")
    private var musicAssistantUrl by mutableStateOf("")
    private var musicAssistantToken by mutableStateOf("")
    private var musicAssistantConnectionVerified by mutableStateOf(false)
    private var musicAssistantConnectionMessage by mutableStateOf<String?>(null)
    private var musicAssistantConnectionTesting by mutableStateOf(false)
    private var musicRequestsUrl by mutableStateOf("")
    private var musicRequestsApiKey by mutableStateOf("")
    private var bookRequestsUrl by mutableStateOf("")
    private var bookRequestsApiKey by mutableStateOf("")
    private var audiobookLibraryUrl by mutableStateOf("")
    private var audiobookLibraryToken by mutableStateOf("")
    private var pinConfigured by mutableStateOf(false)
    private var profileImageUrl by mutableStateOf<String?>(null)
    private var avatarSuggestions by mutableStateOf<List<BaseItemDto>>(emptyList())
    private var selectedAvatarTitle by mutableStateOf<BaseItemDto?>(null)
    private var characterSuggestions by mutableStateOf<List<VesperCharacterOption>>(emptyList())
    private var characterLoading by mutableStateOf(false)
    private var avatarBusy by mutableStateOf(false)
    private var avatarMessage by mutableStateOf<String?>(null)

    private val characterProvider by lazy { VesperCharacterProvider(preferences) }

    private val avatarPhotoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) updateAvatarFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPreferences()
        tvdbConnectionVerified = preferences.getBoolean("tvdb_connection_verified", false)
        musicAssistantConnectionVerified =
            preferences.getBoolean("music_assistant_connection_verified", false)

        val currentUser = userRepository.currentUser.value
        val currentServer = serverRepository.currentServer.value
        profileImageUrl = currentUser?.let { user ->
            val revision = preferences.getLong(avatarRevisionKey(user.id), 0L)
            if (revision > 0L) {
                api.imageApi.getUserImageUrl(userId = user.id, tag = revision.toString())
            } else {
                user.primaryImage?.getUrl(api)
            }
        }
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
        loadAvatarSuggestions()

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
                avatarSuggestions = avatarSuggestions,
                selectedAvatarTitle = selectedAvatarTitle,
                characterSuggestions = characterSuggestions,
                characterLoading = characterLoading,
                avatarBusy = avatarBusy,
                avatarMessage = avatarMessage,
                api = api,
                appVersion = appVersion,
                jellyfinName = currentServer?.name ?: "Media Server",
                showMiniPlayer = showMiniPlayer,
                popularityScope = popularityScope,
                tmdbApiKey = tmdbApiKey,
                tvdbApiKey = tvdbApiKey,
                tvdbSubscriberPin = tvdbSubscriberPin,
                tvdbConnectionVerified = tvdbConnectionVerified,
                tvdbConnectionMessage = tvdbConnectionMessage,
                tvdbConnectionTesting = tvdbConnectionTesting,
                seerrApiKey = seerrApiKey,
                musicAssistantUrl = musicAssistantUrl,
                musicAssistantToken = musicAssistantToken,
                musicAssistantConnectionVerified = musicAssistantConnectionVerified,
                musicAssistantConnectionMessage = musicAssistantConnectionMessage,
                musicAssistantConnectionTesting = musicAssistantConnectionTesting,
                musicRequestsUrl = musicRequestsUrl,
                musicRequestsApiKey = musicRequestsApiKey,
                bookRequestsUrl = bookRequestsUrl,
                bookRequestsApiKey = bookRequestsApiKey,
                audiobookLibraryUrl = audiobookLibraryUrl,
                audiobookLibraryToken = audiobookLibraryToken,
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
                    musicAssistantConnectionVerified = false
                    musicAssistantConnectionMessage = null
                    preferences.edit()
                        .putString("music_assistant_url", musicAssistantUrl)
                        .putString("music_assistant_token", musicAssistantToken)
                        .putBoolean("music_assistant_connection_verified", false)
                        .apply()
                    markChanged()
                },
                onTestMusicAssistant = { url, token ->
                    musicAssistantUrl = url.trim().trimEnd('/')
                    musicAssistantToken = token.trim()
                    musicAssistantConnectionTesting = true
                    musicAssistantConnectionMessage = null

                    lifecycleScope.launch {
                        val result = runCatching {
                            val endpoint = VesperMusicEndpoint.from(musicAssistantUrl)
                            withContext(Dispatchers.IO) {
                                MusicAssistantClient(
                                    baseUrl = endpoint.baseUrl,
                                    token = musicAssistantToken,
                                ).validateConnection()
                            }
                            endpoint
                        }

                        musicAssistantConnectionTesting = false
                        result.fold(
                            onSuccess = { endpoint ->
                                musicAssistantConnectionVerified = true
                                musicAssistantConnectionMessage = if (endpoint.remoteReady) {
                                    "Music Assistant API connected over HTTPS."
                                } else {
                                    "Music Assistant API connected. This address is local-only."
                                }
                                preferences.edit()
                                    .putString("music_assistant_url", endpoint.baseUrl)
                                    .putString("music_assistant_token", musicAssistantToken)
                                    .putBoolean("music_assistant_connection_verified", true)
                                    .apply()
                                markChanged()
                            },
                            onFailure = { failure ->
                                musicAssistantConnectionVerified = false
                                musicAssistantConnectionMessage =
                                    failure.message ?: "Music Assistant connection failed."
                                preferences.edit()
                                    .putBoolean("music_assistant_connection_verified", false)
                                    .apply()
                            },
                        )
                    }
                },
                onSaveDiscoveryRequests = { musicUrl, musicKey, booksUrl, booksKey, audioUrl, audioToken ->
                    musicRequestsUrl = musicUrl.trim().trimEnd('/')
                    musicRequestsApiKey = musicKey.trim()
                    bookRequestsUrl = booksUrl.trim().trimEnd('/')
                    bookRequestsApiKey = booksKey.trim()
                    audiobookLibraryUrl = audioUrl.trim().trimEnd('/')
                    audiobookLibraryToken = audioToken.trim()
                    preferences.edit()
                        .putString("music_requests_url", musicRequestsUrl)
                        .putString("music_requests_api_key", musicRequestsApiKey)
                        .putString("book_requests_url", bookRequestsUrl)
                        .putString("book_requests_api_key", bookRequestsApiKey)
                        .putString("audiobook_library_url", audiobookLibraryUrl)
                        .putString("audiobook_library_token", audiobookLibraryToken)
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
                onSaveTvdb = { key, pin ->
                    tvdbApiKey = key.trim()
                    tvdbSubscriberPin = pin.trim()
                    tvdbConnectionVerified = false
                    tvdbConnectionMessage = null
                    preferences.edit()
                        .putString("tvdb_api_key", tvdbApiKey)
                        .putString("tvdb_subscriber_pin", tvdbSubscriberPin)
                        .putBoolean("tvdb_connection_verified", false)
                        .remove("tvdb_access_token")
                        .remove("tvdb_access_token_created_at")
                        .remove("tvdb_access_token_fingerprint")
                        .apply()
                    characterSuggestions = emptyList()
                    selectedAvatarTitle = null
                    avatarMessage = null
                    markChanged()
                },
                onTestTvdb = { key, pin ->
                    tvdbApiKey = key.trim()
                    tvdbSubscriberPin = pin.trim()
                    tvdbConnectionTesting = true
                    tvdbConnectionMessage = null

                    lifecycleScope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                characterProvider.validateCredentials(
                                    apiKey = tvdbApiKey,
                                    subscriberPin = tvdbSubscriberPin.ifBlank { null },
                                )
                            }
                        }

                        tvdbConnectionTesting = false
                        result.fold(
                            onSuccess = {
                                tvdbConnectionVerified = true
                                tvdbConnectionMessage = "TheTVDB connection is working."
                                preferences.edit()
                                    .putString("tvdb_api_key", tvdbApiKey)
                                    .putString("tvdb_subscriber_pin", tvdbSubscriberPin)
                                    .putBoolean("tvdb_connection_verified", true)
                                    .apply()
                                markChanged()
                            },
                            onFailure = { failure ->
                                tvdbConnectionVerified = false
                                tvdbConnectionMessage = failure.message ?: "TheTVDB connection failed."
                                preferences.edit()
                                    .putBoolean("tvdb_connection_verified", false)
                                    .apply()
                            },
                        )
                    }
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
                onPickAvatarPhoto = {
                    avatarPhotoPicker.launch("image/*")
                },
                onChooseAvatarTitle = { item ->
                    loadCharacterSuggestions(item)
                },
                onUseCharacter = { character ->
                    updateAvatarFromCharacter(character)
                },
                onOpenTvdb = {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://thetvdb.com/api-information/signup"),
                        )
                    )
                },
                onOpenJellyfinSettings = {
                    startActivity(
                        Intent(this, PreferencesActivity::class.java)
                            .putExtra(PreferencesActivity.EXTRA_VESPER_MOBILE_ENTRY, true)
                    )
                },
            )
        }
    }

    private fun loadAvatarSuggestions() {
        lifecycleScope.launch {
            avatarMessage = null
            val suggestions = runCatching {
                withContext(Dispatchers.IO) {
                    val allItems = mutableListOf<BaseItemDto>()
                    var startIndex = 0
                    var totalRecordCount: Int? = null

                    do {
                        val page = api.itemsApi.getItems(
                            fields = ItemRepository.browseFields,
                            includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            collapseBoxSetItems = false,
                            startIndex = startIndex,
                            limit = CHARACTER_LIBRARY_PAGE_SIZE,
                            imageTypeLimit = 1,
                            sortBy = setOf(ItemSortBy.SORT_NAME),
                            sortOrder = setOf(SortOrder.ASCENDING),
                            enableTotalRecordCount = true,
                        ).content

                        if (totalRecordCount == null) {
                            totalRecordCount = page.totalRecordCount
                        }

                        allItems += page.items
                        startIndex += page.items.size
                    } while (
                        page.items.isNotEmpty() &&
                        startIndex < (totalRecordCount ?: startIndex)
                    )

                    allItems
                        .filter { item ->
                            (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.SERIES) &&
                                item.itemImages[ImageType.PRIMARY] != null
                        }
                        .distinctBy { it.id }
                }
            }.getOrElse {
                avatarMessage = "Couldn't load your full Vesper library for character selection."
                emptyList()
            }
            avatarSuggestions = suggestions
        }
    }

    private fun updateAvatarFromUri(uri: Uri) {
        lifecycleScope.launch {
            avatarBusy = true
            avatarMessage = null

            val result = runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                        ?: error("Couldn't read that image.")
                }
                uploadProfileAvatar(bytes)
            }

            avatarBusy = false
            avatarMessage = result.fold(
                onSuccess = { "Profile picture updated." },
                onFailure = { "Couldn't update the profile picture." },
            )
        }
    }

    private fun loadCharacterSuggestions(item: BaseItemDto) {
        selectedAvatarTitle = item
        characterSuggestions = emptyList()
        avatarMessage = null

        if (tvdbApiKey.isBlank()) {
            avatarMessage = "Set up TheTVDB in Connections to browse character artwork."
            return
        }

        lifecycleScope.launch {
            characterLoading = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    characterProvider.getCharacters(
                        item = item,
                        apiKey = tvdbApiKey,
                        subscriberPin = tvdbSubscriberPin.ifBlank { null },
                    )
                }
            }
            characterLoading = false

            result.fold(
                onSuccess = { characters ->
                    characterSuggestions = characters
                    avatarMessage = if (characters.isEmpty()) {
                        "No role-specific character artwork was found for ${item.name ?: "this title"}."
                    } else {
                        null
                    }
                },
                onFailure = { failure ->
                    avatarMessage = failure.message ?: "Couldn't load character artwork."
                },
            )
        }
    }

    private fun updateAvatarFromCharacter(character: VesperCharacterOption) {
        lifecycleScope.launch {
            avatarBusy = true
            avatarMessage = null

            val result = runCatching {
                val imageBytes = withContext(Dispatchers.IO) {
                    characterProvider.downloadImage(character.imageUrl)
                }
                uploadProfileAvatar(imageBytes)
            }

            avatarBusy = false
            avatarMessage = result.fold(
                onSuccess = { "Using ${character.name} as your profile picture." },
                onFailure = { failure ->
                    failure.message ?: "Couldn't use that character artwork as the profile picture."
                },
            )
        }
    }

    private suspend fun uploadProfileAvatar(sourceBytes: ByteArray) {
        val currentUser = userRepository.currentUser.value ?: error("No active Jellyfin user.")
        val jpegBytes = withContext(Dispatchers.IO) {
            squareAvatarJpeg(sourceBytes)
        }

        val encodedImage = Base64.encode(jpegBytes, Base64.NO_WRAP)

        api.imageApi.postUserImage(
            userId = currentUser.id,
            data = FileInfo(
                content = encodedImage,
                mediaType = "image/jpeg",
            ),
        )

        val revision = System.currentTimeMillis()
        preferences.edit()
            .putLong(avatarRevisionKey(currentUser.id), revision)
            .apply()

        profileImageUrl = api.imageApi.getUserImageUrl(
            userId = currentUser.id,
            tag = revision.toString(),
        )
        markChanged()
    }

    private fun squareAvatarJpeg(sourceBytes: ByteArray): ByteArray {
        val source = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: error("Unsupported image.")

        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2

        val cropped = Bitmap.createBitmap(source, left, top, side, side)
        val scaled = if (side == 640) cropped else Bitmap.createScaledBitmap(cropped, 640, 640, true)

        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 92, output)

        if (scaled !== cropped) scaled.recycle()
        if (cropped !== source) cropped.recycle()
        source.recycle()

        return output.toByteArray()
    }

    private fun avatarRevisionKey(userId: java.util.UUID): String =
        "profile_avatar_revision_$userId"

    private fun loadPreferences() {
        showMiniPlayer = preferences.getBoolean("show_persistent_mini_player", true)
        popularityScope = preferences.getString("popularity_scope", "GLOBAL") ?: "GLOBAL"
        tmdbApiKey = preferences.getString("tmdb_api_key", "").orEmpty()
        tvdbApiKey = preferences.getString("tvdb_api_key", "").orEmpty()
        tvdbSubscriberPin = preferences.getString("tvdb_subscriber_pin", "").orEmpty()
        seerrApiKey = preferences.getString("seerr_api_key", "").orEmpty()
        musicAssistantUrl = preferences
            .getString("music_assistant_url", "http://192.168.1.34:8095")
            .orEmpty()
        musicAssistantToken = preferences.getString("music_assistant_token", "").orEmpty()
        musicRequestsUrl = preferences.getString("music_requests_url", "").orEmpty()
        musicRequestsApiKey = preferences.getString("music_requests_api_key", "").orEmpty()
        bookRequestsUrl = preferences.getString("book_requests_url", "").orEmpty()
        bookRequestsApiKey = preferences.getString("book_requests_api_key", "").orEmpty()
        audiobookLibraryUrl = preferences.getString("audiobook_library_url", "").orEmpty()
        audiobookLibraryToken = preferences.getString("audiobook_library_token", "").orEmpty()
    }

    private fun markChanged() {
        setResult(RESULT_OK)
    }

    private companion object {
        const val CHARACTER_LIBRARY_PAGE_SIZE = 250
    }
}

private enum class SettingsPage {
    MAIN,
    PROFILE,
    JELLYFIN,
    MUSIC_ASSISTANT,
    DISCOVERY_REQUESTS,
    SEERR,
    TMDB,
    TVDB,
}

@Composable
private fun VesperSettingsScreen(
    page: SettingsPage,
    userName: String,
    userAvatarUrl: String?,
    userId: java.util.UUID?,
    serverId: java.util.UUID?,
    pinConfigured: Boolean,
    avatarSuggestions: List<BaseItemDto>,
    selectedAvatarTitle: BaseItemDto?,
    characterSuggestions: List<VesperCharacterOption>,
    characterLoading: Boolean,
    avatarBusy: Boolean,
    avatarMessage: String?,
    api: ApiClient,
    appVersion: String,
    jellyfinName: String,
    showMiniPlayer: Boolean,
    popularityScope: String,
    tmdbApiKey: String,
    tvdbApiKey: String,
    tvdbSubscriberPin: String,
    tvdbConnectionVerified: Boolean,
    tvdbConnectionMessage: String?,
    tvdbConnectionTesting: Boolean,
    seerrApiKey: String,
    musicAssistantUrl: String,
    musicAssistantToken: String,
    musicAssistantConnectionVerified: Boolean,
    musicAssistantConnectionMessage: String?,
    musicAssistantConnectionTesting: Boolean,
    musicRequestsUrl: String,
    musicRequestsApiKey: String,
    bookRequestsUrl: String,
    bookRequestsApiKey: String,
    audiobookLibraryUrl: String,
    audiobookLibraryToken: String,
    onBack: () -> Unit,
    onPage: (SettingsPage) -> Unit,
    onMiniPlayer: (Boolean) -> Unit,
    onPopularity: (String) -> Unit,
    onSaveMusicAssistant: (String, String) -> Unit,
    onTestMusicAssistant: (String, String) -> Unit,
    onSaveDiscoveryRequests: (String, String, String, String, String, String) -> Unit,
    onSaveSeerr: (String) -> Unit,
    onSaveTmdb: (String) -> Unit,
    onSaveTvdb: (String, String) -> Unit,
    onTestTvdb: (String, String) -> Unit,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit,
    onPickAvatarPhoto: () -> Unit,
    onChooseAvatarTitle: (BaseItemDto) -> Unit,
    onUseCharacter: (VesperCharacterOption) -> Unit,
    onOpenTvdb: () -> Unit,
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
                tvdbConfigured = tvdbApiKey.isNotBlank() && tvdbConnectionVerified,
                tvdbVerified = tvdbConnectionVerified,
                seerrConfigured = seerrApiKey.isNotBlank(),
                musicAssistantConfigured = musicAssistantUrl.isNotBlank() && musicAssistantToken.isNotBlank(),
                musicAssistantVerified = musicAssistantConnectionVerified,
                musicAssistantRemoteReady = runCatching {
                    VesperMusicEndpoint.from(musicAssistantUrl).remoteReady
                }.getOrDefault(false),
                discoveryRequestConnections = listOf(
                    musicRequestsUrl.isNotBlank() && musicRequestsApiKey.isNotBlank(),
                    bookRequestsUrl.isNotBlank() && bookRequestsApiKey.isNotBlank(),
                    audiobookLibraryUrl.isNotBlank() && audiobookLibraryToken.isNotBlank(),
                ).count { it },
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
                avatarSuggestions = avatarSuggestions,
                selectedAvatarTitle = selectedAvatarTitle,
                characterSuggestions = characterSuggestions,
                characterLoading = characterLoading,
                avatarBusy = avatarBusy,
                avatarMessage = avatarMessage,
                tvdbConfigured = tvdbApiKey.isNotBlank(),
                api = api,
                onBack = onBack,
                onSetPin = onSetPin,
                onRemovePin = onRemovePin,
                onPickAvatarPhoto = onPickAvatarPhoto,
                onChooseAvatarTitle = onChooseAvatarTitle,
                onUseCharacter = onUseCharacter,
                onConfigureTvdb = { onPage(SettingsPage.TVDB) },
                onOpenTvdb = onOpenTvdb,
            )

            SettingsPage.JELLYFIN -> JellyfinSettingsPage(
                jellyfinName = jellyfinName,
                onBack = onBack,
                onOpenJellyfinSettings = onOpenJellyfinSettings,
            )

            SettingsPage.MUSIC_ASSISTANT -> MusicAssistantSettingsPage(
                initialUrl = musicAssistantUrl,
                initialToken = musicAssistantToken,
                verified = musicAssistantConnectionVerified,
                message = musicAssistantConnectionMessage,
                testing = musicAssistantConnectionTesting,
                onBack = onBack,
                onSave = onSaveMusicAssistant,
                onTest = onTestMusicAssistant,
            )

            SettingsPage.DISCOVERY_REQUESTS -> DiscoveryRequestSettingsPage(
                initialMusicUrl = musicRequestsUrl,
                initialMusicKey = musicRequestsApiKey,
                initialBooksUrl = bookRequestsUrl,
                initialBooksKey = bookRequestsApiKey,
                initialAudioUrl = audiobookLibraryUrl,
                initialAudioToken = audiobookLibraryToken,
                onBack = onBack,
                onSave = onSaveDiscoveryRequests,
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

            SettingsPage.TVDB -> TvdbSettingsPage(
                initialKey = tvdbApiKey,
                initialPin = tvdbSubscriberPin,
                verified = tvdbConnectionVerified,
                message = tvdbConnectionMessage,
                testing = tvdbConnectionTesting,
                onBack = onBack,
                onSave = onSaveTvdb,
                onTest = onTestTvdb,
                onOpenTvdb = onOpenTvdb,
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
    tvdbConfigured: Boolean,
    tvdbVerified: Boolean,
    seerrConfigured: Boolean,
    musicAssistantConfigured: Boolean,
    musicAssistantVerified: Boolean,
    musicAssistantRemoteReady: Boolean,
    discoveryRequestConnections: Int,
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
                        "Choose whether Vesper ranks content using global TMDb activity or your household library.",
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
                    title = "Media Server",
                    subtitle = jellyfinName,
                    status = "Connected",
                    onClick = { onPage(SettingsPage.JELLYFIN) },
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon = "♫",
                    title = "Music Assistant",
                    subtitle = "Music library, rooms and playback",
                    status = when {
                        musicAssistantVerified && musicAssistantRemoteReady -> "HTTPS ready"
                        musicAssistantVerified -> "Local only"
                        musicAssistantConfigured -> "Configured"
                        else -> "Needs setup"
                    },
                    onClick = { onPage(SettingsPage.MUSIC_ASSISTANT) },
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon = "S",
                    title = "Video Requests",
                    subtitle = "Movies and TV requests",
                    status = if (seerrConfigured) "Connected" else "Needs setup",
                    onClick = { onPage(SettingsPage.SEERR) },
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon = "⌕",
                    title = "Music & Book Requests",
                    subtitle = "Music, books and audiobooks in Search",
                    status = when (discoveryRequestConnections) {
                        0 -> "Needs setup"
                        3 -> "Configured"
                        else -> "$discoveryRequestConnections of 3"
                    },
                    onClick = { onPage(SettingsPage.DISCOVERY_REQUESTS) },
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon = "T",
                    title = "TMDb",
                    subtitle = "Global popularity and metadata",
                    status = if (tmdbConfigured) "Configured" else "Needs setup",
                    onClick = { onPage(SettingsPage.TMDB) },
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon = "C",
                    title = "TheTVDB",
                    subtitle = "Character artwork for Vesper profiles",
                    status = when {
                        tvdbVerified -> "Connected"
                        tvdbConfigured -> "Configured"
                        else -> "Needs setup"
                    },
                    onClick = { onPage(SettingsPage.TVDB) },
                )
            }
        }

        item { SettingsSectionLabel("ADVANCED") }

        item {
            SettingsCard {
                SettingsNavigationRow(
                    icon = "⚙",
                    title = "Advanced playback settings",
                    subtitle = "Playback, subtitles and client options",
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
    avatarSuggestions: List<BaseItemDto>,
    selectedAvatarTitle: BaseItemDto?,
    characterSuggestions: List<VesperCharacterOption>,
    characterLoading: Boolean,
    avatarBusy: Boolean,
    avatarMessage: String?,
    tvdbConfigured: Boolean,
    api: ApiClient,
    onBack: () -> Unit,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit,
    onPickAvatarPhoto: () -> Unit,
    onChooseAvatarTitle: (BaseItemDto) -> Unit,
    onUseCharacter: (VesperCharacterOption) -> Unit,
    onConfigureTvdb: () -> Unit,
    onOpenTvdb: () -> Unit,
) {
    var characterTitleQuery by remember { mutableStateOf("") }
    val filteredAvatarTitles = remember(avatarSuggestions, characterTitleQuery) {
        val query = characterTitleQuery.trim()
        if (query.isBlank()) {
            avatarSuggestions
        } else {
            avatarSuggestions.filter { item ->
                item.name?.contains(query, ignoreCase = true) == true
            }
        }
    }

    SettingsDetailScaffold(
        title = "Profile & Security",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                VesperProfileAvatar(
                    name = userName,
                    imageUrl = userAvatarUrl,
                    modifier = Modifier
                        .size(88.dp)
                        .clickable(enabled = !avatarBusy, onClick = onPickAvatarPhoto),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6E55E8))
                        .border(2.dp, Color(0xFF090A11), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        "✎",
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            BasicText(
                userName,
                style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(5.dp))
            BasicText(
                if (avatarBusy) "Updating profile picture…"
                else "Upload your own photo or choose a character from Vesper.",
                style = TextStyle(color = Color(0xFF858E9A), fontSize = 11.sp),
            )
            Spacer(Modifier.height(14.dp))
            SettingsPrimaryButton(
                label = if (avatarBusy) "Updating…" else "Choose a photo",
                onClick = onPickAvatarPhoto,
                enabled = !avatarBusy,
            )
            if (!avatarMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    avatarMessage,
                    style = TextStyle(
                        color = if (
                            avatarMessage.startsWith("Couldn't") ||
                            avatarMessage.startsWith("No role")
                        ) Color(0xFFFFB7BE) else Color(0xFFA8D9C1),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    ),
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        SettingsSectionLabel("CHOOSE A CHARACTER")
        Spacer(Modifier.height(7.dp))

        if (!tvdbConfigured) {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    BasicText(
                        "Character artwork needs TheTVDB",
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(5.dp))
                    BasicText(
                        "Vesper only shows role-specific images here — actor headshots are deliberately filtered out.",
                        style = TextStyle(color = Color(0xFF858E9A), fontSize = 11.sp, lineHeight = 16.sp),
                    )
                    Spacer(Modifier.height(13.dp))
                    SettingsPrimaryButton(
                        label = "Set up TheTVDB",
                        onClick = onConfigureTvdb,
                    )
                }
            }
        } else if (avatarSuggestions.isNotEmpty()) {
            BasicText(
                "Search your Movies & TV library, then choose one of the title's real character images.",
                style = TextStyle(color = Color(0xFF858E9A), fontSize = 11.sp, lineHeight = 16.sp),
            )
            Spacer(Modifier.height(11.dp))

            SettingsField(
                label = "Find a title",
                value = characterTitleQuery,
                onValueChange = { characterTitleQuery = it },
                placeholder = "Toy Story, Frozen, Harry Potter…",
            )

            Spacer(Modifier.height(12.dp))

            if (filteredAvatarTitles.isEmpty()) {
                BasicText(
                    "No movies or series matched that search.",
                    style = TextStyle(color = Color(0xFF858E9A), fontSize = 11.sp),
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(
                        items = filteredAvatarTitles,
                        key = { it.id },
                    ) { item ->
                        AvatarTitleTile(
                            item = item,
                            api = api,
                            selected = selectedAvatarTitle?.id == item.id,
                            enabled = !avatarBusy && !characterLoading,
                            onClick = { onChooseAvatarTitle(item) },
                        )
                    }
                }
            }

            if (selectedAvatarTitle != null) {
                Spacer(Modifier.height(18.dp))
                BasicText(
                    selectedAvatarTitle.name ?: "Characters",
                    style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(9.dp))

                when {
                    characterLoading -> BasicText(
                        "Finding character artwork…",
                        style = TextStyle(color = Color(0xFF9AA3AF), fontSize = 11.sp),
                    )

                    characterSuggestions.isNotEmpty() -> LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(
                            items = characterSuggestions,
                            key = { it.name + it.imageUrl },
                        ) { character ->
                            CharacterSuggestionTile(
                                character = character,
                                enabled = !avatarBusy,
                                onClick = { onUseCharacter(character) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                BasicText(
                    "Character artwork provided by TheTVDB",
                    modifier = Modifier.clickable(onClick = onOpenTvdb),
                    style = TextStyle(
                        color = Color(0xFF8F82D3),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
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

@Composable
private fun AvatarTitleTile(
    item: BaseItemDto,
    api: ApiClient,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val imageUrl = item.itemImages[ImageType.PRIMARY]?.getUrl(
        api = api,
        maxWidth = 240,
        maxHeight = 360,
    )

    Column(
        modifier = Modifier.width(82.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(76.dp)
                .height(108.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF151A24))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color(0xFF8C72FF) else Color(0x334D4A75),
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = imageUrl,
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        BasicText(
            item.name ?: "Media",
            style = TextStyle(
                color = if (selected) Color.White else Color(0xFFBBC2CC),
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 2,
        )
    }
}

@Composable
private fun CharacterSuggestionTile(
    character: VesperCharacterOption,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(Color(0xFF151A24))
                .border(1.dp, Color(0x445E55A6), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                url = character.imageUrl,
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP,
            )
        }
        Spacer(Modifier.height(7.dp))
        BasicText(
            character.name,
            style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 2,
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
        title = "Media Server",
        onBack = onBack,
    ) {
        SettingsInfoCard(
            title = jellyfinName,
            value = VesperServiceConfig.JELLYFIN_BASE_URL,
            helper = "Vesper uses this canonical HTTPS endpoint on Wi-Fi and mobile data.",
        )
        Spacer(Modifier.height(16.dp))
        SettingsPrimaryButton(
            label = "Advanced playback settings",
            onClick = onOpenJellyfinSettings,
        )
    }
}

@Composable
private fun MusicAssistantSettingsPage(
    initialUrl: String,
    initialToken: String,
    verified: Boolean,
    message: String?,
    testing: Boolean,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onTest: (String, String) -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var token by remember(initialToken) { mutableStateOf(initialToken) }
    val endpoint = remember(url) {
        runCatching { VesperMusicEndpoint.from(url) }.getOrNull()
    }

    SettingsDetailScaffold(
        title = "Music Assistant",
        onBack = onBack,
    ) {
        SettingsInfoCard(
            title = if (endpoint?.remoteReady == true) "Canonical HTTPS endpoint" else "Local Music Assistant endpoint",
            value = endpoint?.baseUrl ?: url.ifBlank { "Not configured" },
            helper = if (endpoint?.remoteReady == true) {
                "Vesper will use this same secure host for the MA API and This Device playback."
            } else {
                "This address only works on your home network. Use an HTTPS endpoint for Music Anywhere."
            },
        )
        Spacer(Modifier.height(16.dp))
        SettingsField(
            label = "Server URL",
            value = url,
            onValueChange = { url = it },
            placeholder = "https://music.example.com",
        )
        Spacer(Modifier.height(14.dp))
        SettingsField(
            label = "Access token",
            value = token,
            onValueChange = { token = it },
            placeholder = "Music Assistant token",
            password = true,
        )
        Spacer(Modifier.height(12.dp))

        if (endpoint != null) {
            SettingsInfoCard(
                title = "This Device transport",
                value = endpoint.sendspinUrl,
                helper = if (endpoint.remoteReady) {
                    "Secure WebSocket endpoint derived automatically from the canonical HTTPS address."
                } else {
                    "Local Sendspin uses Music Assistant's player port. Remote playback still needs HTTPS."
                },
            )
            Spacer(Modifier.height(14.dp))
        }

        SettingsPrimaryButton(
            label = if (testing) "Testing…" else "Test connection",
            onClick = { onTest(url, token) },
            enabled = url.isNotBlank() && token.isNotBlank() && !testing,
        )
        Spacer(Modifier.height(9.dp))
        SettingsSecondaryButton(
            label = "Save credentials",
            onClick = { onSave(url, token) },
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                message,
                style = TextStyle(
                    color = if (verified) Color(0xFFA8D9C1) else Color(0xFFFFB7BE),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                ),
            )
        }
    }
}

@Composable
private fun DiscoveryRequestSettingsPage(
    initialMusicUrl: String,
    initialMusicKey: String,
    initialBooksUrl: String,
    initialBooksKey: String,
    initialAudioUrl: String,
    initialAudioToken: String,
    onBack: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
) {
    var musicUrl by remember(initialMusicUrl) { mutableStateOf(initialMusicUrl) }
    var musicKey by remember(initialMusicKey) { mutableStateOf(initialMusicKey) }
    var booksUrl by remember(initialBooksUrl) { mutableStateOf(initialBooksUrl) }
    var booksKey by remember(initialBooksKey) { mutableStateOf(initialBooksKey) }
    var audioUrl by remember(initialAudioUrl) { mutableStateOf(initialAudioUrl) }
    var audioToken by remember(initialAudioToken) { mutableStateOf(initialAudioToken) }

    SettingsDetailScaffold(
        title = "Music & Book Requests",
        onBack = onBack,
    ) {
        SettingsInfoCard(
            title = "Vesper Search",
            value = "Music · Books · Audiobooks",
            helper = "These connections stay behind Vesper's Search UI. Use HTTPS addresses if you want discovery and requests away from home.",
        )

        Spacer(Modifier.height(18.dp))
        SettingsSectionLabel("MUSIC REQUESTS")
        Spacer(Modifier.height(9.dp))
        SettingsField(
            label = "Service URL",
            value = musicUrl,
            onValueChange = { musicUrl = it },
            placeholder = "https://music-requests.example.com",
        )
        Spacer(Modifier.height(10.dp))
        SettingsField(
            label = "API key",
            value = musicKey,
            onValueChange = { musicKey = it },
            placeholder = "Music Requests API key",
            password = true,
        )

        Spacer(Modifier.height(22.dp))
        SettingsSectionLabel("BOOK & AUDIOBOOK REQUESTS")
        Spacer(Modifier.height(9.dp))
        SettingsField(
            label = "Service URL",
            value = booksUrl,
            onValueChange = { booksUrl = it },
            placeholder = "https://book-requests.example.com",
        )
        Spacer(Modifier.height(10.dp))
        SettingsField(
            label = "API key",
            value = booksKey,
            onValueChange = { booksKey = it },
            placeholder = "Book Requests API key",
            password = true,
        )

        Spacer(Modifier.height(22.dp))
        SettingsSectionLabel("AUDIOBOOK LIBRARY")
        Spacer(Modifier.height(9.dp))
        SettingsField(
            label = "Service URL",
            value = audioUrl,
            onValueChange = { audioUrl = it },
            placeholder = "https://audiobooks.example.com",
        )
        Spacer(Modifier.height(10.dp))
        SettingsField(
            label = "Access token",
            value = audioToken,
            onValueChange = { audioToken = it },
            placeholder = "Audiobook Library token",
            password = true,
        )

        Spacer(Modifier.height(22.dp))
        SettingsPrimaryButton(
            label = "Save connections",
            onClick = {
                onSave(
                    musicUrl,
                    musicKey,
                    booksUrl,
                    booksKey,
                    audioUrl,
                    audioToken,
                )
            },
            enabled =
                (musicUrl.isBlank() == musicKey.isBlank()) &&
                    (booksUrl.isBlank() == booksKey.isBlank()) &&
                    (audioUrl.isBlank() == audioToken.isBlank()),
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
private fun TvdbSettingsPage(
    initialKey: String,
    initialPin: String,
    verified: Boolean,
    message: String?,
    testing: Boolean,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onTest: (String, String) -> Unit,
    onOpenTvdb: () -> Unit,
) {
    var key by remember(initialKey) { mutableStateOf(initialKey) }
    var pin by remember(initialPin) { mutableStateOf(initialPin) }

    SettingsDetailScaffold(
        title = "TheTVDB",
        onBack = onBack,
    ) {
        SettingsInfoCard(
            title = "Character artwork",
            value = "TheTVDB API v4",
            helper = "Vesper uses role-specific character images and filters out generic actor headshots. The subscriber PIN is optional and only needed for user-supported API access.",
        )
        Spacer(Modifier.height(16.dp))
        SettingsField(
            label = "Project API key",
            value = key,
            onValueChange = { key = it },
            placeholder = "TheTVDB API key",
            password = true,
        )
        Spacer(Modifier.height(14.dp))
        SettingsField(
            label = "Subscriber PIN (optional)",
            value = pin,
            onValueChange = { pin = it },
            placeholder = "Leave blank if your key doesn't require one",
            password = true,
        )
        Spacer(Modifier.height(14.dp))
        SettingsSecondaryButton(
            label = "Get / manage a TheTVDB API key",
            onClick = onOpenTvdb,
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            "Character artwork metadata provided by TheTVDB.",
            modifier = Modifier.clickable(onClick = onOpenTvdb),
            style = TextStyle(color = Color(0xFF8F82D3), fontSize = 10.sp),
        )
        Spacer(Modifier.height(18.dp))
        SettingsPrimaryButton(
            label = if (testing) "Testing…" else "Test connection",
            onClick = { onTest(key, pin) },
            enabled = key.isNotBlank() && !testing,
        )
        Spacer(Modifier.height(9.dp))
        SettingsSecondaryButton(
            label = "Save credentials",
            onClick = { onSave(key, pin) },
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                message,
                style = TextStyle(
                    color = if (verified) Color(0xFFA8D9C1) else Color(0xFFFFB7BE),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                ),
            )
        }
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
