package com.undef.manoslocales.ui.database

import com.google.firebase.firestore.Exclude

data class User(
    @get:Exclude
    var id: String = "",

    // El resto de tus campos se mantienen igual, con sus valores por defecto.
    val nombre: String = "",
    val apellido: String = "",
    val phone: String = "",
    val email: String = "",
    val profileImageUrl: String = "",
    val categoria: String? = null,
    val city: String? = null,
    val role: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,

    @get:Exclude val password: String = ""
)
