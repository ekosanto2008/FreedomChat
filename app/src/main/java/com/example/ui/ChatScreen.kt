package com.example.ui

import com.example.ChatSessionManager

import android.widget.Toast
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.data.Message
import com.example.data.SupabaseService
import com.example.data.UserProfile
import com.example.presentation.AuthState
import com.example.presentation.ChatState
import com.example.presentation.ChatViewModel
import com.example.presentation.ContactsState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val snackbarEvent by viewModel.snackbarEvent.collectAsStateWithLifecycle()

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    viewModel.trackPresence(isOnline = true)
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    viewModel.trackPresence(isOnline = false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.trackPresence(isOnline = false)
        }
    }

    // Auth redirection observer
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                navController.navigate("contacts") {
                    popUpTo("splash") { inclusive = true }
                }
            }
            is AuthState.Unauthenticated -> {
                navController.navigate("auth") {
                    popUpTo(0) { inclusive = true }
                }
            }
            else -> {}
        }
    }

    // Direct navigation observer for incoming notifications
    LaunchedEffect(viewModel) {
        viewModel.navigateToChatEvent.collect {
            navController.navigate("private_chat")
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("splash") {
                SplashScreen()
            }
            composable("auth") {
                AuthScreen(viewModel = viewModel)
            }
            composable("contacts") {
                ContactListScreen(
                    viewModel = viewModel,
                    onContactSelected = { contact ->
                        viewModel.startPrivateChat(contact)
                        navController.navigate("private_chat")
                    },
                    onNavigateToProfile = {
                        navController.navigate("profile")
                    }
                )
            }
            composable("profile") {
                ProfileScreen(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable("private_chat") {
                PrivateChatScreen(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        // Global Customizable Enterprise Snackbar Overlay
        AnimatedVisibility(
            visible = snackbarEvent != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            snackbarEvent?.let { (msg, type) ->
                CustomSnackbar(
                    message = msg,
                    type = type,
                    onDismiss = { viewModel.clearSnackbar() }
                )
            }
        }
    }
}

/**
 * CUSTOM SNACKBAR COMPOSABLE
 */
@Composable
fun CustomSnackbar(
    message: String,
    type: String, // "success" or "error"
    onDismiss: () -> Unit
) {
    LaunchedEffect(message) {
        delay(3500)
        onDismiss()
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (type == "success") Color(0xFF2E7D32) else Color(0xFFC62828),
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("custom_snackbar")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (type == "success") "✅" else "❌",
                fontSize = 18.sp
            )
            Text(
                text = message,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Text("✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * SCREEN 1: AUTHENTICATION (Login & Register)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: ChatViewModel
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isSupabaseMode by viewModel.isSupabaseMode.collectAsStateWithLifecycle()

    var isRegisterState by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F6FD))
    ) {
        // Floating ambient light-purple background glowing blobs
        Box(
            modifier = Modifier
                .offset(x = (-80).dp, y = (-80).dp)
                .size(320.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFE5D9FA).copy(alpha = 0.6f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = 80.dp)
                .size(240.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFDFD1FF).copy(alpha = 0.45f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 100.dp)
                .size(280.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFE5D9FA).copy(alpha = 0.5f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        // Main content container with resilient scroll support
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Title: Freedom Messenger
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Freedom Messenger",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F0235),
                    letterSpacing = (-0.5).sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .background(Color(0xFF8F63FF), shape = RoundedCornerShape(2.dp))
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(Color(0xFF8F63FF), shape = CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Glassmorphic App Logo Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, Color.White),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(112.dp)
                    .border(1.dp, Color(0xFFEFEBF9), RoundedCornerShape(28.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFEFE6FF), Color(0xFFDCD0FF))
                            ),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Speech bubble with 3 cute purple dots
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.White, shape = RoundedCornerShape(22.dp, 22.dp, 4.dp, 22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF8F63FF), shape = CircleShape))
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF8F63FF), shape = CircleShape))
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF8F63FF), shape = CircleShape))
                        }
                    }
                }
            }

            // Screen Subtitle State Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isRegisterState) "Buat Akun Baru" else "Selamat Datang Kembali",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F0235)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val indicatorColor = if (isSupabaseMode) Color(0xFF2ECC71) else Color(0xFFFF9F1C)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(indicatorColor, shape = CircleShape)
                    )
                    Text(
                        text = if (isSupabaseMode) "Supabase Live Connected" else "Demo Sandbox Simulator Mode",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = indicatorColor
                    )
                }
            }

            // Soft White Inner Card containing the Styled Inputs
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFEFEBF9).copy(alpha = 0.8f), RoundedCornerShape(26.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Email input field
                    CustomAuthTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "Email Address",
                        icon = Icons.Default.Email,
                        testTag = "email_input"
                    )

                    // Username input field (only visible in RegisterState)
                    AnimatedVisibility(visible = isRegisterState) {
                        CustomAuthTextField(
                            value = username,
                            onValueChange = { username = it },
                            placeholder = "Unique Username",
                            icon = Icons.Default.Person,
                            testTag = "username_input"
                        )
                    }

                    // Password input field
                    CustomAuthTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Password",
                        icon = Icons.Default.Lock,
                        testTag = "password_input",
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color(0xFF9E99B2),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Gradient Submit Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF9F70FD), Color(0xFF635BFF))
                                ),
                                shape = RoundedCornerShape(100.dp)
                            )
                            .clickable(
                                enabled = authState !is AuthState.Loading,
                                onClick = {
                                    if (email.isBlank() || password.isBlank() || (isRegisterState && username.isBlank())) {
                                        viewModel.showSnackbar("Harap lengkapi semua isian", "error")
                                        return@clickable
                                    }
                                    if (password.length < 6) {
                                        viewModel.showSnackbar("Password minimal 6 karakter!", "error")
                                        return@clickable
                                    }

                                    if (isRegisterState) {
                                        viewModel.signUp(
                                            email = email,
                                            password = password,
                                            username = username,
                                            onSuccess = {
                                                viewModel.showSnackbar("Akun berhasil didaftarkan!", "success")
                                            },
                                            onFailure = { err ->
                                                viewModel.showSnackbar("Gagal: $err", "error")
                                            }
                                        )
                                    } else {
                                        viewModel.signIn(
                                            email = email,
                                            password = password,
                                            onSuccess = {
                                                viewModel.showSnackbar("Berhasil masuk, selamat datang!", "success")
                                            },
                                            onFailure = { err ->
                                                viewModel.showSnackbar("Email atau Password salah: $err", "error")
                                            }
                                        )
                                    }
                                }
                            )
                            .testTag(if (isRegisterState) "register_button" else "login_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (authState is AuthState.Loading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = if (isRegisterState) "Daftar Akun" else "Masuk",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // Link to Switch States
            if (isRegisterState) {
                Row(
                    modifier = Modifier
                        .clickable { isRegisterState = false }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sudah punya akun? ",
                        color = Color(0xFF7D7893),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Masuk",
                        color = Color(0xFF635BFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .clickable { isRegisterState = true }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Belum punya akun? ",
                        color = Color(0xFF7D7893),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Daftar gratis",
                        color = Color(0xFF635BFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Custom Input component for premium credentials handling
 */
@Composable
fun CustomAuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    testTag: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFFFAF9FD), shape = RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFEFEBF9), shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Container with soft lavender box styling
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFEFEBF9), shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF8F63FF),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // BasicTextField for total container customization
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .testTag(testTag),
            textStyle = TextStyle(
                color = Color(0xFF0F0235),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            ),
            singleLine = true,
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color(0xFF9E99B2),
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (trailingIcon != null) {
            trailingIcon()
        }
    }
}

/**
 * SCREEN 2: CONTACT LIST SCREEN
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    viewModel: ChatViewModel,
    onContactSelected: (UserProfile) -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isSupabaseMode by viewModel.isSupabaseMode.collectAsStateWithLifecycle()

    var showAddContactDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var detailContactToShow by remember { mutableStateOf<UserProfile?>(null) }

    val currentProfile = when (val auth = authState) {
        is AuthState.Authenticated -> auth.user
        else -> UserProfile("demo", "DimasChat", "demo@example.com")
    }

    Scaffold(
        topBar = {}, // Hidden standard TopAppBar to render custom high-fidelity wave header inside the body Column
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddContactDialog = true },
                containerColor = Color(0xFF8F63FF),
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("add_contact_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Contact",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        if (showSettingsDialog) {
            SettingsDialog(onDismiss = { showSettingsDialog = false })
        }

        if (detailContactToShow != null) {
            ContactDetailsPopupDialog(
                contact = detailContactToShow!!,
                isOnline = false, // Reactive status defaults or checked offline as mockup
                onDismiss = { detailContactToShow = null },
                onOpenChat = {
                    onContactSelected(detailContactToShow!!)
                    detailContactToShow = null
                },
                onOpenProfile = {
                    onContactSelected(detailContactToShow!!)
                    detailContactToShow = null
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF15142E), Color(0xFF0D0B1A))
                    )
                )
        ) {
            // High fidelity custom curved header (Wave equivalent)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF15142E))
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Profile info clickable on left
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToProfile() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF8F63FF).copy(alpha = 0.15f),
                            modifier = Modifier
                                .size(50.dp)
                                .border(1.5.dp, Color(0xFF8B93FF).copy(alpha = 0.3f), CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                if (!currentProfile.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = currentProfile.avatarUrl,
                                        contentDescription = "My Avatar",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = currentProfile.username.take(2).uppercase(),
                                        color = Color(0xFFC7B6FF),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Chat Privat",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("✏️", fontSize = 15.sp)
                            }
                            Text(
                                text = "@${currentProfile.username}",
                                fontSize = 13.sp,
                                color = Color(0xFF9E86FF),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Three dots icon inside raised circular/square card on right
                    Box {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF1E1A36).copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, Color(0xFF8B93FF).copy(alpha = 0.2f)),
                            modifier = Modifier
                                .size(46.dp)
                                .clickable { showMenu = true }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Pengaturan") },
                                onClick = {
                                    showMenu = false
                                    showSettingsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                onClick = {
                                    showMenu = false
                                    viewModel.signOut(onSuccess = {
                                        viewModel.showSnackbar("Berhasil logout", "success")
                                    })
                                }
                            )
                        }
                    }
                }
            }

            // Elegant Active Session floating card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1A33).copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, Color(0xFF8B93FF).copy(alpha = 0.15f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Soft green circular background with green dot in center
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFE8F8F0).copy(alpha = 0.15f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF2ECD71), shape = CircleShape)
                            )
                        }
                        Text(
                            text = if (isSupabaseMode) "Supabase Sesi Aktif" else "Supabase Sesi Aktif (Local Simulator)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFAFAAC4)
                        )
                    }

                    // Shield badge icon on correct side
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF1E1A36), shape = CircleShape)
                            .border(1.dp, Color(0xFF8B93FF).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🛡️", fontSize = 14.sp)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = contactsState) {
                    is ContactsState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF8F63FF))
                        }
                    }
                    is ContactsState.Error -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(text = "Masalah: ${state.message}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        }
                    }
                    is ContactsState.Success -> {
                        if (state.contacts.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Tidak Ada Teman 👥",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Klik tombol tambah (+) di bawah untuk menambahkan kawan obrolan baru.",
                                    fontSize = 13.sp,
                                    color = Color(0xFFAFAAC4),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.contacts) { contact ->
                                    ContactItem(
                                        contact = contact,
                                        viewModel = viewModel,
                                        onContactSelected = onContactSelected,
                                        onAvatarClick = { detailContactToShow = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddContactDialog) {
            AddContactDialog(
                onDismiss = { showAddContactDialog = false },
                onAdd = { username ->
                    viewModel.addContactByUsername(
                        username = username,
                        onSuccess = { info ->
                            viewModel.showSnackbar(info, "success")
                            showAddContactDialog = false
                        },
                        onFailure = { err ->
                            viewModel.showSnackbar("Kesalahan: $err", "error")
                        }
                    )
                }
            )
        }
    }
}
 /**
 * SCREEN 3: PROFILE SCREEN
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val profile = when (val auth = authState) {
        is AuthState.Authenticated -> auth.user
        else -> null
    }

    var username by remember { mutableStateOf(profile?.username ?: "") }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var mimeType by remember { mutableStateOf<String?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                val stream = context.contentResolver.openInputStream(uri)
                selectedImageBytes = stream?.readBytes()
                mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                viewModel.showSnackbar("Foto profil terpilih!", "success")
            } catch (e: Exception) {
                viewModel.showSnackbar("Gagal membaca foto", "error")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sunting Profil", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF160A3A)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color(0xFF160A3A))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFEDE9FE), Color(0xFFFBF9FF))
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // High fidelity Double-bordered profile picture
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .size(124.dp)
                    .clickable { imageLauncher.launch("image/*") }
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxSize(),
                    border = BorderStroke(2.dp, Color(0xFFEDE9FE))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .border(3.dp, Color(0xFF8F63FF), CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFFEADDFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else if (!profile?.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = profile?.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = (profile?.username ?: "U").take(2).uppercase(),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF160A3A)
                            )
                        }
                    }
                }

                // Camera overlay button
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF8F63FF),
                    modifier = Modifier.size(36.dp),
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("📷", fontSize = 16.sp, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // INFO CARDS FOR DATA REPRESENTATION
            // Card 1: Email Row
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFECE9FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Color(0xFF8F63FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text("Email", fontSize = 11.sp, color = Color(0xFF9894A9))
                        Text(
                            text = profile?.email ?: "Belum Atur Email",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF160A3A)
                        )
                    }
                }
            }

            // Card 2: Username Row (Read-Only)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFECE9FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF8F63FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text("Username (Tidak Dapat Diubah)", fontSize = 11.sp, color = Color(0xFF9894A9))
                        Text(
                            text = profile?.username ?: "User",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF160A3A)
                        )
                    }
                }
            }

            // Card 3: Joining Date Mockup
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFECE9FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF8F63FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text("Bergabung sejak", fontSize = 11.sp, color = Color(0xFF9894A9))
                        Text(
                            text = "1 Januari 2026",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF160A3A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save changes pill button in nice indigo-purple gradient
            Button(
                onClick = {
                    if (username.isBlank()) {
                        viewModel.showSnackbar("Username tidak boleh kosong!", "error")
                        return@Button
                    }
                    viewModel.updateProfileAndAvatar(
                        newUsername = username,
                        imageBytes = selectedImageBytes,
                        mimeType = mimeType,
                        onSuccess = {
                            viewModel.showSnackbar("Sunting profil sukses!", "success")
                            onBack()
                        },
                        onFailure = { err ->
                            viewModel.showSnackbar("Profil gagal diperbarui: $err", "error")
                        }
                    )
                },
                enabled = !isSending,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("profile_save_button")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF9E86FF), Color(0xFF635BFF))
                            ),
                            shape = RoundedCornerShape(100.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💾", fontSize = 16.sp)
                            Text("Simpan Perubahan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * SCREEN 4: PRIVATE CHAT SCREEN
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    val activeContact by viewModel.activeContact.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isSupabaseMode by viewModel.isSupabaseMode.collectAsStateWithLifecycle()
    val presenceList by viewModel.presenceList.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var textMessage by remember { mutableStateOf("") }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var previewImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var imageCaption by remember { mutableStateOf("") }

    val activeRoomId by viewModel.activeRoomId.collectAsStateWithLifecycle()
    var selectedImageUri by remember { mutableStateOf<String?>(null) }
    var messageToEdit by remember { mutableStateOf<Message?>(null) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var showClearChatDialog by remember { mutableStateOf(false) }

    val contactName = activeContact?.username ?: "Teman"
    val myProfile = when (val auth = authState) {
        is AuthState.Authenticated -> auth.user
        else -> UserProfile("demo", "DimasChat", "demo@example.com")
    }

    // Live partner presence and typing status mapper
    val activePartnerPresence = presenceList.find { it.userId == activeContact?.id }
    val isPartnerOnline = activePartnerPresence?.isOnline == true
    val isPartnerTyping = activePartnerPresence?.isTyping == true

    val liveStatusLabel = when {
        isPartnerTyping -> "typing..."
        isPartnerOnline -> "Online"
        else -> "Offline"
    }

    val liveStatusColor = when {
        isPartnerTyping -> Color(0xFF6750A4)
        isPartnerOnline -> Color(0xFF2E7D32)
        else -> Color(0xFF49454F)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearActiveConversation()
            Log.d("ChatLifecycle", "User keluar dari room, Active Conversation dikosongkan.")
        }
    }

    DisposableEffect(activeRoomId) {
        ChatSessionManager.activeConversationId = activeRoomId
        onDispose {
            ChatSessionManager.activeConversationId = null
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val messages = (chatState as? ChatState.Success)?.messages ?: emptyList()

    LaunchedEffect(messages.size, lifecycleState, activeRoomId) {
        val convId = activeRoomId
        if (convId != null && lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED) {
            viewModel.markMessagesAsRead(convId)
        }
    }

    // Typing activity trigger flow
    LaunchedEffect(textMessage) {
        if (textMessage.isNotEmpty()) {
            viewModel.setTyping(true)
            delay(1800)
            viewModel.setTyping(false)
        } else {
            viewModel.setTyping(false)
        }
    }

    val attachmentImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            previewImageUri = uri
            imageCaption = ""
        }
    }

    if (showProfileDialog && activeContact != null) {
        ContactDetailsPopupDialog(
            contact = activeContact!!,
            isOnline = isPartnerOnline,
            onDismiss = { showProfileDialog = false },
            onOpenChat = { showProfileDialog = false },
            onOpenProfile = { showProfileDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF15122B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.clickable { showProfileDialog = true }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFECE9FF),
                            modifier = Modifier.size(38.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8F63FF))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!activeContact?.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = activeContact?.avatarUrl,
                                        contentDescription = "Contact photo",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = contactName.take(2).uppercase(),
                                        color = Color(0xFF160A3A),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Column {
                            Text(
                                text = contactName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (isPartnerTyping || isPartnerOnline) Color(0xFF2ECD71) else Color(0xFF9E9E9E),
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = liveStatusLabel,
                                    fontSize = 11.sp,
                                    color = if (isPartnerTyping || isPartnerOnline) Color(0xFF2ECD71) else Color(0xFF8F8A9F),
                                    fontWeight = if (isPartnerTyping || isPartnerOnline) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Pengaturan") },
                                onClick = {
                                    showMenu = false
                                    showSettingsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bersihkan Chat") },
                                onClick = {
                                    showMenu = false
                                    showClearChatDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                onClick = {
                                    showMenu = false
                                    viewModel.signOut(onSuccess = {
                                        viewModel.showSnackbar("Berhasil logout", "success")
                                    })
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showSettingsDialog) {
            SettingsDialog(onDismiss = { showSettingsDialog = false })
        }
        if (showClearChatDialog) {
            AlertDialog(
                onDismissRequest = { showClearChatDialog = false },
                title = { Text("Bersihkan Chat") },
                text = { Text("Apakah Anda yakin ingin menghapus semua pesan di percakapan ini untuk kedua belah pihak? Tindakan ini permanen.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            activeRoomId?.let { roomId ->
                                viewModel.clearConversationMessages(
                                    conversationId = roomId,
                                    onSuccess = {
                                        viewModel.showSnackbar("Semua percakapan berhasil dibersihkan", "success")
                                    },
                                    onFailure = { err ->
                                        viewModel.showSnackbar("Gagal membersihkan percakapan: $err", "error")
                                    }
                                )
                            }
                            showClearChatDialog = false
                        }
                    ) {
                        Text("Hapus")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearChatDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }
        if (messageToEdit != null) {
            var editMsgText by remember { mutableStateOf(messageToEdit!!.text) }
            AlertDialog(
                onDismissRequest = { messageToEdit = null },
                title = { Text("Ubah Pesan") },
                text = {
                    val focusRequester = remember { FocusRequester() }
                    OutlinedTextField(
                        value = editMsgText,
                        onValueChange = { editMsgText = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        label = { Text("Pesan baru") }
                    )
                    LaunchedEffect(Unit) {
                        try {
                            focusRequester.requestFocus()
                        } catch (e: Exception) {}
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val msg = messageToEdit!!
                            viewModel.editMessage(
                                messageId = msg.id ?: "",
                                newText = editMsgText.trim(),
                                onSuccess = {
                                    viewModel.showSnackbar("Pesan berhasil diubah", "success")
                                },
                                onFailure = { err ->
                                    viewModel.showSnackbar("Gagal mengubah pesan: $err", "error")
                                }
                            )
                            messageToEdit = null
                        }
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { messageToEdit = null }) {
                        Text("Batal")
                    }
                }
            )
        }
        if (messageToDelete != null) {
            AlertDialog(
                onDismissRequest = { messageToDelete = null },
                title = { Text("Hapus Pesan") },
                text = { Text("Apakah Anda yakin ingin menghapus pesan ini untuk semua orang?") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            val msg = messageToDelete!!
                            viewModel.deleteMessage(
                                messageId = msg.id ?: "",
                                onSuccess = {
                                    viewModel.showSnackbar("Pesan berhasil dihapus", "success")
                                },
                                onFailure = { err ->
                                    viewModel.showSnackbar("Gagal menghapus pesan: $err", "error")
                                }
                            )
                            messageToDelete = null
                        }
                    ) {
                        Text("Hapus")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { messageToDelete = null }) {
                        Text("Batal")
                    }
                }
            )
        }
        if (selectedImageUri != null) {
            FullScreenImageDialog(selectedImageUri!!) {
                selectedImageUri = null
            }
        }
        if (previewImageUri != null) {
            AttachmentPreviewDialog(
                imageUri = previewImageUri!!,
                caption = imageCaption,
                onCaptionChange = { imageCaption = it },
                onDismiss = {
                    previewImageUri = null
                    imageCaption = ""
                },
                onSend = {
                    val uri = previewImageUri!!
                    try {
                        val stream = context.contentResolver.openInputStream(uri)
                        val bytes = stream?.readBytes()
                        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val name = uri.lastPathSegment ?: "photo.jpeg"
                        if (bytes != null) {
                            val finalCaption = imageCaption.trim().ifBlank { "[Lampiran Media Gambar 📸]" }
                            viewModel.sendAttachment(
                                fileName = name,
                                mimeType = mime,
                                bytes = bytes,
                                text = finalCaption,
                                onSuccess = {
                                    viewModel.showSnackbar("Gambar terkirim!", "success")
                                },
                                onFailure = { err ->
                                    viewModel.showSnackbar("Gagal mengirim: $err", "error")
                                }
                            )
                        } else {
                            viewModel.showSnackbar("Gagal membaca data gambar", "error")
                        }
                    } catch (e: Exception) {
                        viewModel.showSnackbar("Gagal memproses visual lampiran: ${e.message}", "error")
                    }
                    previewImageUri = null
                    imageCaption = ""
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF15122B), Color(0xFF0D0B1A))
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = chatState) {
                    is ChatState.Idle -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Menginisialisasi obrolan...", color = Color(0xFF7C788F))
                        }
                    }
                    is ChatState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF8F63FF))
                        }
                    }
                    is ChatState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Gagal memuat pesan: ${state.message}", textAlign = TextAlign.Center, color = Color.Red)
                        }
                    }
                    is ChatState.Success -> {
                        if (state.messages.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🔐 Pesan terenkripsi & privat", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF160A3A))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Mulai percakapan aman dengan $contactName.", fontSize = 12.sp, color = Color(0xFF7C788F), textAlign = TextAlign.Center)
                            }
                        } else {
                            PrivateMessageList(
                                messages = state.messages,
                                currentUserId = myProfile.id,
                                contactName = contactName,
                                onImageClick = { url -> selectedImageUri = url },
                                onEditMessage = { msg -> messageToEdit = msg },
                                onDeleteMessage = { msg -> messageToDelete = msg }
                            )
                        }
                    }
                }
            }

            // Secure socket state indicator
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1A36).copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color(0xFF8B93FF).copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("🛡️", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSupabaseMode) "Saluran enkripsi WebSocket aktif (aman)" else "Sesi Kunci Sandboxed Simulator Terverifikasi",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFAFAAC4)
                    )
                }
            }

            // Modern white input container bar
            Surface(
                color = Color(0xFF131127),
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .background(Color(0xFF1E1A36))
                            .border(1.dp, Color(0xFF8B93FF).copy(alpha = 0.15f), RoundedCornerShape(25.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("😊", fontSize = 18.sp)
                        androidx.compose.foundation.text.BasicTextField(
                            value = textMessage,
                            onValueChange = { textMessage = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_input_field"),
                            maxLines = 2,
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (textMessage.isEmpty()) {
                                        Text(
                                            text = "Tulis pesan privat...",
                                            color = Color(0xFFAFAAC4),
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        IconButton(
                            onClick = { attachmentImageLauncher.launch("image/*") },
                            modifier = Modifier.size(28.dp).testTag("attachment_button")
                        ) {
                            Text("📎", fontSize = 18.sp)
                        }
                    }

                    // Direct beautiful gradient FAB button for message dispatch
                    Button(
                        onClick = {
                            val msg = textMessage
                            if (msg.isNotBlank() && !isSending) {
                                viewModel.sendPrivateMessage(
                                    text = msg,
                                    onSuccess = {
                                        textMessage = ""
                                    },
                                    onFailure = { err ->
                                        viewModel.showSnackbar("Pesan gagal terkirim: $err", "error")
                                    }
                                )
                            }
                        },
                        enabled = textMessage.isNotBlank() && !isSending,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color(0xFF221F38)),
                        contentPadding = PaddingValues(),
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("send_message_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = if (textMessage.isNotBlank()) {
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF9E86FF), Color(0xFF635BFF))
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF221F38), Color(0xFF221F38))
                                        )
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Kirim",
                                tint = if (textMessage.isNotBlank()) Color.White else Color(0xFF8F8A9F),
                                modifier = Modifier.size(20.dp)
                             )
                        }
                    }
                }
            }
        }
    }
}

/**
 * PRIVATE MESSAGE BOARD
 */
@Composable
fun PrivateMessageList(
    messages: List<Message>,
    currentUserId: String,
    contactName: String,
    onImageClick: (String) -> Unit,
    onEditMessage: (Message) -> Unit,
    onDeleteMessage: (Message) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val reversedMessages = messages.reversed()
        items(reversedMessages, key = { it.id ?: it.hashCode() }) { message ->
            val isCurrentUser = message.senderId == currentUserId
            PrivateMessageBubble(
                message = message,
                isCurrentUser = isCurrentUser,
                contactName = contactName,
                onImageClick = onImageClick,
                onEditClick = { onEditMessage(message) },
                onDeleteClick = { onDeleteMessage(message) }
            )
        }
    }
}

@Composable
fun PrivateMessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    contactName: String,
    onImageClick: (String) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val bubbleShape = if (isCurrentUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    val containerColor = if (isCurrentUser) Color(0xFF8F63FF) else Color(0xFF1E1A36)
    val contentColor = Color.White
    val horizontalAlign = if (isCurrentUser) Alignment.End else Alignment.Start

    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = horizontalAlign
    ) {
        val nameLabel = if (isCurrentUser) "Anda" else contactName

        Text(
            text = nameLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFAFAAC4),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )

        Box {
            Card(
                shape = bubbleShape,
                colors = CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                border = if (isCurrentUser) null else BorderStroke(1.dp, Color(0xFF8B93FF).copy(alpha = 0.15f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .widthIn(max = 285.dp)
                    .testTag("chat_message_card")
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                if (isCurrentUser) {
                                    showMenu = true
                                }
                            }
                        )
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (!message.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = "Lampiran gambar",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .padding(bottom = 6.dp)
                                .clickable { onImageClick(message.imageUrl) },
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }

                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = contentColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val formattedTime = parseIsoLocalTime(message.timestamp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = formattedTime,
                            fontSize = 10.sp,
                            color = if (isCurrentUser) Color.White.copy(alpha = 0.75f) else Color(0xFF9894A9)
                        )
                        if (isCurrentUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            if (message.isRead) {
                                Box(modifier = Modifier.size(width = 17.dp, height = 13.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Terbaca",
                                        tint = Color(0xFFECE9FF),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFFECE9FF),
                                        modifier = Modifier
                                            .size(13.dp)
                                            .offset(x = 4.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Terkirim",
                                    tint = Color(0xFFDCD5FF),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (isCurrentUser) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Ubah Pesan ✏️") },
                        onClick = {
                            showMenu = false
                            onEditClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus untuk Semua Orang 🗑️") },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Full screen view",
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clickable {
                            try {
                                val uri = android.net.Uri.parse(imageUrl)
                                val request = android.app.DownloadManager.Request(uri).apply {
                                    setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    setDestinationInExternalPublicDir(
                                        android.os.Environment.DIRECTORY_DOWNLOADS,
                                        uri.lastPathSegment ?: "chat_image_${System.currentTimeMillis()}.jpg"
                                    )
                                    setTitle("Mengunduh Gambar")
                                    setDescription("Mengunduh lampiran gambar chat...")
                                    setAllowedOverMetered(true)
                                    setAllowedOverRoaming(true)
                                }
                                val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                downloadManager.enqueue(request)
                                android.widget.Toast.makeText(context, "Mulai mengunduh...", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Gagal mengunduh: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                ) {
                    Text(
                        text = "Download 📥",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AttachmentPreviewDialog(
    imageUri: android.net.Uri,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Image preview
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Preview Gambar",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp), // spare room for bottom caption
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )

                // Close Button Top Start
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Batal",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Kirim Gambar",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(20.dp)
                )

                // Input Caption and Send Button at Bottom
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = onCaptionChange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("caption_input_field"),
                        placeholder = {
                            Text(
                                "Tambahkan keterangan... 📝",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.White.copy(alpha = 0.12f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                            disabledContainerColor = Color.Transparent,
                            focusedBorderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        ),
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp)
                    )

                    FloatingActionButton(
                        onClick = onSend,
                        containerColor = Color(0xFF008080), // Modern teal matching our app branding
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("send_attachment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Kirim",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * CONTACT DETAILS POPUP DIALOG
 */
@Composable
fun ContactDetailsPopupDialog(
    contact: UserProfile,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131127)),
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFF9E86FF).copy(alpha = 0.5f), Color(0xFF635BFF).copy(alpha = 0.2f)))),
            modifier = Modifier
                .width(320.dp)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Large Avatar Circle with Star/Sparkle
                Box(
                    modifier = Modifier.size(126.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .border(
                                BorderStroke(
                                    1.5.dp,
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF9E86FF),
                                            Color(0xFF635BFF).copy(alpha = 0.3f)
                                        )
                                    )
                                ),
                                CircleShape
                            )
                            .padding(6.dp)
                            .background(Color(0xFFDCD5FF), CircleShape)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!contact.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = contact.avatarUrl,
                                contentDescription = "Contact Profile",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = contact.username.take(2).uppercase(),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF160A3A)
                            )
                        }
                    }
                    // Star/sparkle ornament positioned exactly on the top-right rim
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-6).dp, y = 6.dp)
                    ) {
                        Text("✦", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Name & Status Details
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = contact.username,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isOnline) Color(0xFF2ECD71) else Color(0xFF9E9E9E), CircleShape)
                        )
                        Text(
                            text = if (isOnline) "• Online" else "• Offline",
                            fontSize = 13.sp,
                            color = if (isOnline) Color(0xFF2ECD71) else Color(0xFF9894A9),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Profil Teman Section Divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF252140))
                    Text(
                        text = "Profil teman",
                        fontSize = 11.sp,
                        color = Color(0xFF9894A9),
                        fontWeight = FontWeight.Light,
                        letterSpacing = 0.5.sp
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF252140))
                }

                // Spaced circular buttons: Telepon, Video, Info
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Item 1: Telepon
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable { /* Action */ }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF231E40), CircleShape)
                                .border(1.dp, Color(0xFF8B93FF).copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Telepon",
                                tint = Color(0xFFB5A5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text("Telepon", fontSize = 12.sp, color = Color(0xFF9894A9), fontWeight = FontWeight.Medium)
                    }

                    // Item 2: Video
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable { /* Action */ }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF231E40), CircleShape)
                                .border(1.dp, Color(0xFF8B93FF).copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Video",
                                tint = Color(0xFFB5A5FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text("Video", fontSize = 12.sp, color = Color(0xFF9894A9), fontWeight = FontWeight.Medium)
                    }

                    // Item 3: Info
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable { onDismiss(); onOpenProfile() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF231E40), CircleShape)
                                .border(1.dp, Color(0xFF8B93FF).copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = Color(0xFFB5A5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text("Info", fontSize = 12.sp, color = Color(0xFF9894A9), fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Lihat Profil gradient button
                Button(
                    onClick = { onDismiss(); onOpenProfile() },
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF9E86FF), Color(0xFF635BFF))
                                ),
                                shape = RoundedCornerShape(100.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Lihat Profil", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                // Tutup outlines transparent button
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, Color(0xFF4C3C8C).copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * ADD CONTACT DIALOG
 */
@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (username: String) -> Unit
) {
    var username by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131127)),
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFF9E86FF).copy(alpha = 0.5f), Color(0xFF635BFF).copy(alpha = 0.2f)))),
            modifier = Modifier
                .width(320.dp)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glow avatar and summary in a horizontal row to match reference screenshot perfectly
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Glowing plus circle left
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF33235E), Color(0xFF1B1238))
                                ),
                                shape = CircleShape
                            )
                            .border(
                                BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFF9E86FF).copy(alpha = 0.6f), Color(0xFF635BFF).copy(alpha = 0.2f)))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFFB5A5FF),
                                modifier = Modifier.fillMaxSize().padding(2.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color(0xFF8F63FF), CircleShape)
                                    .align(Alignment.BottomEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Texts right
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Tambah Teman",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Masukkan username unik teman Anda untuk menambahkannya ke daftar chat privat.",
                            fontSize = 11.sp,
                            color = Color(0xFFAFAAC4),
                            lineHeight = 15.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = { Text("Username", color = Color(0xFF706B8F)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF9E86FF)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF9E86FF),
                        unfocusedBorderColor = Color(0xFF32285E),
                        focusedContainerColor = Color(0xFF1B1736),
                        unfocusedContainerColor = Color(0xFF1B1736)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_dialog_confirm_button")
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Text("Batal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Button(
                        onClick = { if (username.isNotBlank()) onAdd(username) },
                        enabled = username.isNotBlank(),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color(0xFF3B2E6E)
                        ),
                        contentPadding = PaddingValues(),
                        modifier = Modifier
                            .width(130.dp)
                            .height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = if (username.isNotBlank()) {
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF9E86FF), Color(0xFF635BFF))
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF4C3C8C), Color(0xFF3B2E6E))
                                        )
                                    },
                                    shape = RoundedCornerShape(100.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tambah", color = if (username.isNotBlank()) Color.White else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * UTILS FOR TIMESTAMP PROCESSING
 */
private fun parseIsoLocalTime(isoString: String): String {
    return try {
        val sdfSource = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        sdfSource.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdfSource.parse(isoString) ?: throw Exception("Failed to parse")
        val sdfDest = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdfDest.timeZone = TimeZone.getDefault()
        sdfDest.format(date)
    } catch (e: Exception) {
        try {
            val tIndex = isoString.indexOf('T')
            if (tIndex != -1 && isoString.length >= tIndex + 6) {
                isoString.substring(tIndex + 1, tIndex + 6)
            } else {
                "baru saja"
            }
        } catch (ex: Exception) {
            "baru saja"
        }
    }
}

/**
 * SETTINGS DIALOG (Configure local notification features and custom ringtones)
 */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("settings_pref", android.content.Context.MODE_PRIVATE) }
    
    var notificationsEnabled by remember {
        mutableStateOf(sharedPref.getBoolean("notifications_enabled", true))
    }
    
    var ringtoneUri by remember {
        mutableStateOf(sharedPref.getString("ringtone_uri", "") ?: "")
    }
    
    var ringtoneName by remember {
        mutableStateOf("")
    }

    LaunchedEffect(ringtoneUri) {
        if (ringtoneUri.isNotEmpty()) {
            try {
                val uri = android.net.Uri.parse(ringtoneUri)
                val ringtone = android.media.RingtoneManager.getRingtone(context, uri)
                ringtoneName = ringtone?.getTitle(context) ?: "Nada Dering Kustom"
            } catch (e: Exception) {
                ringtoneName = "Nada Dering Terpilih"
            }
        } else {
            ringtoneName = "Bawaan Sistem"
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                ringtoneUri = uri.toString()
                sharedPref.edit().putString("ringtone_uri", ringtoneUri).apply()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pengaturan Notifikasi",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF008080)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aktifkan Notifikasi",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Dapatkan pesan masuk saat aplikasi terbuka",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { isChecked ->
                            notificationsEnabled = isChecked
                            sharedPref.edit().putBoolean("notifications_enabled", isChecked).apply()
                        },
                        modifier = Modifier.testTag("notification_switch")
                    )
                }

                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = notificationsEnabled) {
                            val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Pilih Nada Dering")
                                val existingUri = if (ringtoneUri.isNotEmpty()) android.net.Uri.parse(ringtoneUri) else null
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                            }
                            launcher.launch(intent)
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pilih Nada Dering",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (notificationsEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = ringtoneName,
                            fontSize = 12.sp,
                            color = if (notificationsEnabled) Color(0xFF008080) else Color(0xFF008080).copy(alpha = 0.38f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Pilih",
                        tint = if (notificationsEnabled) Color(0xFF008080) else Color.LightGray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Selesai", color = Color(0xFF008080), fontWeight = FontWeight.Bold)
            }
        },
        modifier = Modifier.testTag("settings_dialog")
    )
}

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Visually polished message airplane logo
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "App Logo",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Supabase Chat",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Secure & Real-time Messaging",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
        }
    }
}

@Composable
fun ContactItem(
    contact: UserProfile,
    viewModel: ChatViewModel,
    onContactSelected: (UserProfile) -> Unit,
    onAvatarClick: (UserProfile) -> Unit
) {
    val conversationIdMap by viewModel.conversationIds.collectAsStateWithLifecycle()
    val convId = conversationIdMap[contact.id]

    // Last Message Flow Call
    val lastMessage by remember(convId) {
        if (convId != null) {
            viewModel.getLastMessageFlow(convId)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }.collectAsStateWithLifecycle(initialValue = null)

    // Unread Count Flow Call (Reactive Room collection per Item)
    val unreadCount by viewModel.getUnreadCountFlow(convId).collectAsState(initial = 0)

    val presenceList by viewModel.presenceList.collectAsStateWithLifecycle()
    val isOnline = presenceList.find { it.userId == contact.id }?.isOnline == true

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onContactSelected(contact) }
            .testTag("contact_item_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1736)),
        border = BorderStroke(1.dp, Color(0xFF8B93FF).copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Avatar with details click triggering
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clickable { onAvatarClick(contact) }
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2C254F),
                    border = BorderStroke(1.5.dp, Color(0xFF8B93FF).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (!contact.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = contact.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = contact.username.take(2).uppercase(),
                                color = Color(0xFFC7B6FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                // Small status dot on bottom right of Avatar
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(Color(0xFF131127), CircleShape)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(if (isOnline) Color(0xFF2ECD71) else Color(0xFF9E9E9E), CircleShape)
                    )
                }
            }

            // Row splits Left (Name & Last Msg) and Right (Time & Badge)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name & Last Message
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.username,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastMessage?.text ?: contact.email,
                        fontSize = 13.sp,
                        color = Color(0xFFAFAAC4),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Time & Badge
                Column(horizontalAlignment = Alignment.End) {
                    val lastMessageTime = lastMessage?.timestamp?.let { parseIsoLocalTime(it) } ?: "baru saja"
                    Text(
                        text = lastMessageTime,
                        color = Color(0xFF8F8A9F),
                        fontSize = 11.sp,
                        fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                    )

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color(0xFF635BFF), shape = CircleShape)
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Spacer to align properly
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
