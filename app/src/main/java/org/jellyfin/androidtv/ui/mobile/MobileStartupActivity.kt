package org.jellyfin.androidtv.ui.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.JellyfinApplication
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.AutomaticAuthenticateMethod
import org.jellyfin.androidtv.auth.model.ConnectedState
import org.jellyfin.androidtv.auth.model.CredentialAuthenticateMethod
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.UnableToConnectState
import org.jellyfin.androidtv.auth.model.User
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.koin.android.ext.android.inject
import java.util.UUID

class MobileStartupActivity : FragmentActivity() {
    private val serverRepository by inject<ServerRepository>()
    private val serverUserRepository by inject<ServerUserRepository>()
    private val authenticationRepository by inject<AuthenticationRepository>()
    private val sessionRepository by inject<SessionRepository>()
    private val userRepository by inject<UserRepository>()

    private var stage by mutableStateOf(LoginStage.SERVER)
    private var address by mutableStateOf("")
    private var server by mutableStateOf<Server?>(null)
    private var users by mutableStateOf<List<User>>(emptyList())
    private var selectedUser by mutableStateOf<User?>(null)
    private var username by mutableStateOf("")
    private var password by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var storedServers by mutableStateOf<List<Server>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val switchServerId = intent.getStringExtra(EXTRA_SWITCH_SERVER_ID)?.let(UUID::fromString)

        if (switchServerId == null && sessionRepository.currentSession.value != null && userRepository.currentUser.value != null) {
            openHome()
            return
        }

        setContent {
            MobileLoginScreen(
                stage = stage,
                address = address,
                server = server,
                users = users,
                selectedUser = selectedUser,
                username = username,
                password = password,
                busy = busy,
                error = error,
                storedServers = storedServers,
                userImage = { s, u -> authenticationRepository.getUserImageUrl(s, u) },
                onAddressChange = { address = it },
                onUsernameChange = { username = it },
                onPasswordChange = { password = it },
                onConnect = ::connect,
                onStoredServer = ::openServer,
                onUser = ::chooseUser,
                onPasswordLogin = ::loginSelectedUser,
                onManual = {
                    username = ""
                    password = ""
                    error = null
                    stage = LoginStage.MANUAL
                },
                onManualLogin = ::manualLogin,
                onBack = ::goBack,
            )
        }

        lifecycleScope.launch {
            serverRepository.loadStoredServers()
            storedServers = serverRepository.storedServers.value

            if (switchServerId != null) {
                val target = serverRepository.getServer(switchServerId, true)
                if (target != null) {
                    openServer(target)
                    return@launch
                }
            }

            val last = storedServers.maxByOrNull { it.dateLastAccessed }
            if (last != null) address = last.address
        }
    }

    private fun connect() {
        val value = address.trim()
        if (value.isBlank() || busy) return
        busy = true
        error = null

        lifecycleScope.launch {
            serverRepository.addServer(value).collect { state ->
                when (state) {
                    is ConnectedState -> {
                        busy = false
                        val connected = serverRepository.getServer(state.id)
                        if (connected == null) error = "Connected, but couldn't load the server."
                        else openServer(connected)
                    }
                    is UnableToConnectState -> {
                        busy = false
                        error = "Couldn't connect to that Jellyfin address."
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun openServer(value: Server) {
        server = value
        address = value.address
        busy = true
        error = null
        stage = LoginStage.USERS

        lifecycleScope.launch {
            val stored = serverUserRepository.getStoredServerUsers(value)
            val storedIds = stored.map { it.id }.toSet()
            val public = serverUserRepository.getPublicServerUsers(value).filterNot { it.id in storedIds }
            users = stored + public
            busy = false
        }
    }

    private fun chooseUser(user: User) {
        val currentServer = server ?: return
        selectedUser = user
        password = ""
        busy = true
        error = null

        lifecycleScope.launch {
            authenticationRepository.authenticate(currentServer, AutomaticAuthenticateMethod(user)).collect { state ->
                when (state) {
                    AuthenticatedState -> {
                        busy = false
                        finishLogin()
                    }
                    RequireSignInState -> {
                        busy = false
                        username = user.name
                        stage = LoginStage.PASSWORD
                    }
                    is ServerVersionNotSupported -> {
                        busy = false
                        error = "This Jellyfin server version isn't supported."
                    }
                    ServerUnavailableState,
                    is ApiClientErrorLoginState -> {
                        busy = false
                        error = "Couldn't sign in as ${user.name}."
                    }
                    AuthenticatingState -> busy = true
                }
            }
        }
    }

    private fun loginSelectedUser() {
        val currentServer = server ?: return
        val user = selectedUser ?: return
        authenticateCredentials(currentServer, user.name, password)
    }

    private fun manualLogin() {
        val currentServer = server ?: return
        if (username.isBlank()) {
            error = "Enter your Jellyfin username."
            return
        }
        authenticateCredentials(currentServer, username.trim(), password)
    }

    private fun authenticateCredentials(currentServer: Server, name: String, pass: String) {
        if (busy) return
        busy = true
        error = null

        lifecycleScope.launch {
            authenticationRepository.authenticate(
                currentServer,
                CredentialAuthenticateMethod(name, pass),
            ).collect { state ->
                when (state) {
                    AuthenticatedState -> {
                        busy = false
                        finishLogin()
                    }
                    is ServerVersionNotSupported -> {
                        busy = false
                        error = "This Jellyfin server version isn't supported."
                    }
                    ServerUnavailableState -> {
                        busy = false
                        error = "The Jellyfin server isn't reachable."
                    }
                    is ApiClientErrorLoginState,
                    RequireSignInState -> {
                        busy = false
                        error = "Username or password wasn't accepted."
                    }
                    AuthenticatingState -> busy = true
                }
            }
        }
    }

    private fun finishLogin() {
        lifecycleScope.launch {
            (application as? JellyfinApplication)?.onSessionStart()
            openHome()
        }
    }

    private fun openHome() {
        startActivity(Intent(this, MobileMainActivity::class.java))
        finish()
    }

    private fun goBack() {
        error = null
        when (stage) {
            LoginStage.SERVER -> finish()
            LoginStage.USERS -> stage = LoginStage.SERVER
            LoginStage.PASSWORD -> stage = LoginStage.USERS
            LoginStage.MANUAL -> stage = LoginStage.USERS
        }
    }

    companion object {
        const val EXTRA_SWITCH_SERVER_ID = "switch_server_id"
    }
}

private enum class LoginStage { SERVER, USERS, PASSWORD, MANUAL }

@Composable
private fun MobileLoginScreen(
    stage: LoginStage,
    address: String,
    server: Server?,
    users: List<User>,
    selectedUser: User?,
    username: String,
    password: String,
    busy: Boolean,
    error: String?,
    storedServers: List<Server>,
    userImage: (Server, User) -> String?,
    onAddressChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onStoredServer: (Server) -> Unit,
    onUser: (User) -> Unit,
    onPasswordLogin: () -> Unit,
    onManual: () -> Unit,
    onManualLogin: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080C))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 28.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        "V",
                        style = TextStyle(color = Color(0xFFBDEBFF), fontSize = 28.sp, fontWeight = FontWeight.Black),
                        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF13222E)).padding(start = 15.dp, top = 8.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        BasicText("Vesper", style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                        BasicText(
                            when (stage) {
                                LoginStage.SERVER -> "Connect to Jellyfin"
                                LoginStage.USERS -> server?.name ?: "Choose a user"
                                LoginStage.PASSWORD -> selectedUser?.name ?: "Sign in"
                                LoginStage.MANUAL -> "Sign in with username"
                            },
                            style = TextStyle(color = Color(0xFF8E9BA8), fontSize = 14.sp),
                        )
                    }
                }
                Spacer(Modifier.height(38.dp))
            }

            when (stage) {
                LoginStage.SERVER -> {
                    item {
                        BasicText("Server address", style = labelStyle)
                        Spacer(Modifier.height(8.dp))
                        VesperTextField(address, onAddressChange, "http://192.168.x.x:8096")
                        Spacer(Modifier.height(14.dp))
                        LoginButton(if (busy) "Connecting…" else "Connect", onConnect, !busy && address.isNotBlank())
                    }
                    if (storedServers.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(32.dp))
                            BasicText("Recent servers", style = sectionStyle)
                            Spacer(Modifier.height(10.dp))
                        }
                        items(storedServers, key = { it.id }) { stored -> ServerRow(stored, onStoredServer) }
                    }
                }

                LoginStage.USERS -> {
                    if (busy) item { MutedText("Loading users…") }
                    else {
                        if (users.isEmpty()) item {
                            MutedText("No public users are visible on this server.")
                            Spacer(Modifier.height(16.dp))
                        }
                        items(users, key = { it.id }) { user -> UserRow(user, server, userImage, onUser) }
                        item {
                            Spacer(Modifier.height(18.dp))
                            SecondaryButton("Use username instead", onManual)
                            Spacer(Modifier.height(12.dp))
                            SecondaryButton("‹ Change server", onBack)
                        }
                    }
                }

                LoginStage.PASSWORD -> item {
                    BasicText("Password for ${selectedUser?.name.orEmpty()}", style = labelStyle)
                    Spacer(Modifier.height(8.dp))
                    VesperTextField(password, onPasswordChange, "Password", password = true)
                    Spacer(Modifier.height(14.dp))
                    LoginButton(if (busy) "Signing in…" else "Sign in", onPasswordLogin, !busy)
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("‹ Back to users", onBack)
                }

                LoginStage.MANUAL -> item {
                    BasicText("Username", style = labelStyle)
                    Spacer(Modifier.height(8.dp))
                    VesperTextField(username, onUsernameChange, "Username")
                    Spacer(Modifier.height(16.dp))
                    BasicText("Password", style = labelStyle)
                    Spacer(Modifier.height(8.dp))
                    VesperTextField(password, onPasswordChange, "Password", password = true)
                    Spacer(Modifier.height(14.dp))
                    LoginButton(if (busy) "Signing in…" else "Sign in", onManualLogin, !busy && username.isNotBlank())
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("‹ Back to users", onBack)
                }
            }

            if (!error.isNullOrBlank()) item {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A171B)).padding(13.dp)) {
                    BasicText(error, style = TextStyle(color = Color(0xFFFFC7CD), fontSize = 14.sp))
                }
            }
        }
    }
}

private val labelStyle = TextStyle(color = Color(0xFFDDE5EC), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val sectionStyle = TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)

@Composable
private fun VesperTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, password: Boolean = false) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF111922)).padding(horizontal = 15.dp, vertical = 14.dp)) {
        if (value.isEmpty()) BasicText(placeholder, style = TextStyle(color = Color(0xFF667583), fontSize = 16.sp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color(0xFF8BD8FF)),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
private fun UserRow(user: User, server: Server?, userImage: (Server, User) -> String?, onClick: (User) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF101821)).clickable { onClick(user) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(27.dp)).background(Color(0xFF1B2D3A))) {
            if (server != null) {
                AsyncImage(modifier = Modifier.fillMaxSize(), url = userImage(server, user), scaleType = ImageView.ScaleType.CENTER_CROP)
            }
            BasicText(user.name.take(1).uppercase(), style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(14.dp))
        BasicText(user.name, style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun ServerRow(server: Server, onClick: (Server) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF101821)).clickable { onClick(server) }.padding(14.dp)) {
        BasicText(server.name, style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(3.dp))
        BasicText(server.address, style = TextStyle(color = Color(0xFF8D9AA8), fontSize = 13.sp))
    }
}

@Composable
private fun LoginButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(if (enabled) Color(0xFFEAF6FC) else Color(0xFF3A444D)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = if (enabled) Color(0xFF071017) else Color(0xFF89939C), fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFF111922)).clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = Color(0xFFD9E1E8), fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun MutedText(value: String) {
    BasicText(value, style = TextStyle(color = Color(0xFF95A2AF), fontSize = 15.sp))
}
