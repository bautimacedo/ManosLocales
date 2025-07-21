package com.undef.manoslocales.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.undef.manoslocales.ui.database.User
import com.undef.manoslocales.ui.database.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavHostController,
    userViewModel: UserViewModel
) {
    val categoryOptions = listOf("Tecnologia", "Herramientas", "Alimentos")
    val context = LocalContext.current

    var user by remember { mutableStateOf<User?>(null) }
    var nombre by remember { mutableStateOf(TextFieldValue("")) }
    var apellido by remember { mutableStateOf(TextFieldValue("")) }
    var telefono by remember { mutableStateOf(TextFieldValue("")) }
    var ciudad by remember { mutableStateOf(TextFieldValue("")) }
    var categoria by remember { mutableStateOf("") }
    var profileImageUrl by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showDropdown by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data?.data != null) {
            selectedImageUri = data.data
            userViewModel.uploadUserProfileImage(data.data!!) { uploadedUrl ->
                uploadedUrl?.let {
                    profileImageUrl = it
                }
            }
        }
    }

    val imagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            imagePickerLauncher.launch(intent)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* No es necesario manejar el resultado explícitamente */ }

    LaunchedEffect(Unit) {
        userViewModel.fetchUserInfo { fetchedUser ->
            user = fetchedUser
            fetchedUser?.let {
                nombre = TextFieldValue(it.nombre)
                apellido = TextFieldValue(it.apellido)
                telefono = TextFieldValue(it.phone)
                ciudad = TextFieldValue(it.city ?: "")
                categoria = it.categoria ?: categoryOptions.first()
                profileImageUrl = it.profileImageUrl
            }
        }
    }

    Scaffold(
        containerColor = Color(0xff3E2C1C)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = selectedImageUri ?: profileImageUrl,
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
                    .clickable {
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            Manifest.permission.READ_MEDIA_IMAGES
                        else
                            Manifest.permission.READ_EXTERNAL_STORAGE

                        val hasPermission = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            val intent = Intent(Intent.ACTION_PICK)
                            intent.type = "image/*"
                            imagePickerLauncher.launch(intent)
                        } else {
                            imagePermissionLauncher.launch(permission)
                        }
                    },
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre", color = Color(0xFFFEFAE0)) },
                modifier = Modifier.fillMaxWidth(),
                colors = profileFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = apellido,
                onValueChange = { apellido = it },
                label = { Text("Apellido", color = Color(0xFFFEFAE0)) },
                modifier = Modifier.fillMaxWidth(),
                colors = profileFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = telefono,
                onValueChange = { telefono = it },
                label = { Text("Teléfono", color = Color(0xFFFEFAE0)) },
                modifier = Modifier.fillMaxWidth(),
                colors = profileFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = ciudad,
                onValueChange = { ciudad = it },
                label = { Text("Ciudad", color = Color(0xFFFEFAE0)) },
                modifier = Modifier.fillMaxWidth(),
                colors = profileFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))
            ExposedDropdownMenuBox(
                expanded = showDropdown,
                onExpandedChange = { showDropdown = !showDropdown }
            ) {
                TextField(
                    value = categoria,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Rubro", color = Color(0xFFFEFAE0)) },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = profileFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false }
                ) {
                    categoryOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                categoria = option
                                showDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val permission = Manifest.permission.ACCESS_FINE_LOCATION
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        val fused = LocationServices.getFusedLocationProviderClient(context)
                        fused.lastLocation.addOnSuccessListener { location: Location? ->
                            val proceed: (Double?, Double?) -> Unit = { la, lo ->
                                user?.let {
                                    val updated = it.copy(
                                        nombre = nombre.text,
                                        apellido = apellido.text,
                                        phone = telefono.text,
                                        city = ciudad.text.trim().lowercase(),
                                        categoria = categoria,
                                        profileImageUrl = profileImageUrl,
                                        lat = la ?: it.lat,
                                        lng = lo ?: it.lng
                                    )
                                    userViewModel.updateUserProfile(updated) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            if (location != null) {
                                proceed(location.latitude, location.longitude)
                            } else {
                                val token = CancellationTokenSource()
                                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                                    .addOnSuccessListener { loc ->
                                        proceed(loc?.latitude, loc?.longitude)
                                    }
                                    .addOnFailureListener { proceed(null, null) }
                            }
                        }
                    } else {
                        locationPermissionLauncher.launch(permission)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xffFEFAE0)),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Guardar cambios", color = Color.Black)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { navController.popBackStack() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xffFEFAE0)),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Cancelar", color = Color.Black)
            }
        }
    }
}

@Composable
private fun profileFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color(0xFFFEFAE0),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedLabelColor = Color(0xFFFEFAE0),
    unfocusedLabelColor = Color.LightGray,
    focusedContainerColor = Color(0xFF5C4033),
    unfocusedContainerColor = Color(0xFF5C4033)
)
