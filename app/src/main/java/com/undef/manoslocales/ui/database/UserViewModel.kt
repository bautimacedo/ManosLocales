package com.undef.manoslocales.ui.database

import android.app.Application
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.undef.manoslocales.ui.data.AuthManager
import com.undef.manoslocales.ui.data.SessionManager
import com.undef.manoslocales.ui.dataclasses.Product
import com.undef.manoslocales.utils.FileUtils

class UserViewModel(
    application: Application,
    private val sessionManager: SessionManager
) : AndroidViewModel(application) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val authManager = AuthManager()

    var loginSuccess = mutableStateOf<Boolean?>(null)
        private set

    var authErrorMessage = mutableStateOf<String?>(null)
        private set

    var currentUser = mutableStateOf<FirebaseUser?>(null)
        private set

    /*** AUTH ***/

    fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    sessionManager.saveLoginState(true, user.uid) // GUARDAMOS EL UID, NO EL EMAIL
                    currentUser.value = user
                    loginSuccess.value = true
                } else {
                    loginSuccess.value = false
                }
            }
            .addOnFailureListener { e ->
                loginSuccess.value = false
                authErrorMessage.value = e.message
            }
    }


    fun logoutUser() {
        sessionManager.logout()
        auth.signOut()
        currentUser.value = null
    }

    fun isUserLoggedIn(): Boolean = sessionManager.isLoggedIn()

    fun getUserRole(onResult: (String?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            firestore.collection("users").document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    onResult(doc.getString("role"))
                }
                .addOnFailureListener { onResult(null) }
        } else onResult(null)
    }


    fun getProviderIdsByName(query: String, onResult: (List<String>) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("role", "provider")
            .get()
            .addOnSuccessListener { result ->
                val ids = result.documents.mapNotNull { doc ->
                    val nombre = doc.getString("nombre") ?: ""
                    val apellido = doc.getString("apellido") ?: ""
                    val fullName = "$nombre $apellido"
                    if (fullName.contains(query, ignoreCase = true)) doc.id else null
                }
                onResult(ids)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun getProviders(onResult: (List<User>) -> Unit) {
        firestore.collection("users").whereEqualTo("role", "provider")
            .get()
            .addOnSuccessListener { snap ->
                val providerList = snap.documents.mapNotNull { doc ->
                    try {
                        val user = doc.toObject(User::class.java)
                        user?.id = doc.id // La corrección clave
                        user
                    } catch (e: Exception) {
                        null
                    }
                }
                onResult(providerList)
            }
            .addOnFailureListener {
                Log.e("UserViewModel", "Error al obtener proveedores", it)
                onResult(emptyList())
            }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    fun changePassword(currentPassword: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email == null) return onResult(false, "Usuario no autenticado")

        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener { onResult(true, null) }
                    .addOnFailureListener { e -> onResult(false, e.message) }
            }
            .addOnFailureListener { e -> onResult(false, "Contraseña actual incorrecta") }
    }

    /*** REGISTER / PROFILE ***/

    fun registerUser(
        email: String,
        password: String,
        nombre: String,
        apellido: String,
        phone: String,
        role: String,
        categoria: String? = null,
        ciudad: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        onComplete: (Boolean) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: return@addOnSuccessListener

                val userMap = mutableMapOf<String, Any>(
                    "email" to email,
                    "nombre" to nombre,
                    "apellido" to apellido,
                    "phone" to phone,
                    "role" to role
                )

                categoria?.let { userMap["categoria"] = it }
                ciudad?.let { userMap["city"] = it.trim().lowercase() }

                if (role == "provider") {
                    userMap["lat"] = lat ?: 0.0
                    userMap["lng"] = lng ?: 0.0
                }

                firestore.collection("users").document(uid).set(userMap)
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { onComplete(false) }
            }
            .addOnFailureListener {
                Log.e("UserViewModel", "Error al crear usuario en Auth", it)
                onComplete(false)
            }
    }


    fun uploadUserProfileImage(uri: Uri, onResult: (String?) -> Unit) {
        val path = FileUtils.getPath(getApplication(), uri)
        if (path != null) {
            MediaManager.get().upload(path)
                .callback(object : UploadCallback {
                    override fun onStart(id: String?) {}
                    override fun onProgress(id: String?, b: Long, t: Long) {}
                    override fun onSuccess(id: String?, data: Map<*, *>) {
                        val url = data["secure_url"] as? String
                        val uid = auth.currentUser?.uid
                        if (uid != null && url != null) {
                            firestore.collection("users").document(uid)
                                .update("profileImageUrl", url)
                                .addOnSuccessListener { onResult(url) }
                                .addOnFailureListener { onResult(null) }
                        } else onResult(null)
                    }
                    override fun onError(id: String?, err: ErrorInfo?) { onResult(null) }
                    override fun onReschedule(id: String?, err: ErrorInfo?) { onResult(null) }
                })
                .dispatch()
        } else onResult(null)
    }

    fun updateUserProfile(updated: User, onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val map = mutableMapOf<String, Any>(
            "nombre" to updated.nombre,
            "apellido" to updated.apellido,
            "phone" to updated.phone
        )
        map["role"] = updated.role
        updated.categoria?.let { map["categoria"] = it }
        updated.city?.let { map["city"] = it.trim().lowercase() }
        if (updated.profileImageUrl.isNotBlank()) map["profileImageUrl"] = updated.profileImageUrl

        if (updated.role == "provider") {
            map["lat"] = updated.lat ?: 0.0
            map["lng"] = updated.lng ?: 0.0
        }

        firestore.collection("users").document(uid)
            .update(map)
            .addOnSuccessListener { onComplete() }
    }

    fun fetchUserInfo(onResult: (User?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(null)
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val user = doc.toObject(User::class.java)
                    user?.id = doc.id // Asignamos el ID
                    onResult(user)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    /*** PRODUCTS ***/

    fun uploadProductImage(uri: Uri, onResult: (String?) -> Unit) {
        val p = FileUtils.getPath(getApplication(), uri) ?: return onResult(null)
        MediaManager.get().upload(p)
            .callback(object : UploadCallback {
                override fun onStart(id: String?) {}
                override fun onProgress(id: String?, b: Long, t: Long) {}
                override fun onSuccess(id: String?, data: Map<*, *>) {
                    val u = data["secure_url"] as? String
                    onResult(u)
                }
                override fun onError(id: String?, err: ErrorInfo?) { onResult(null) }
                override fun onReschedule(id: String?, err: ErrorInfo?) { onResult(null) }
            })
            .dispatch()
    }

    fun createProduct(name: String, description: String, price: Double, imageUrl: String, category: String, city: String, onResult: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return onResult(false, "No autenticado")
        val p = hashMapOf(
            "name" to name, "description" to description, "price" to price,
            "imageUrl" to imageUrl, "providerId" to user.uid,
            "createdAt" to System.currentTimeMillis(), "category" to category,
            "city" to city.trim().lowercase()
        )
        firestore.collection("products")
            .add(p)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    fun getMyProducts(onResult: (List<Product>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(emptyList())
        firestore.collection("products")
            .whereEqualTo("providerId", uid)
            .get()
            .addOnSuccessListener { snap ->
                onResult(snap.documents.mapNotNull { it.toObject(Product::class.java)?.copy(id = it.id) })
            }
    }

    fun deleteProduct(productId: String, onResult: (Boolean, String?) -> Unit) {
        firestore.collection("products").document(productId)
            .delete()
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    fun updateProduct(prod: Product, onResult: (Boolean, String?) -> Unit) {
        val map = mapOf(
            "name" to prod.name,
            "description" to prod.description,
            "price" to prod.price,
            "category" to prod.category,
            "city" to prod.city
        )
        firestore.collection("products").document(prod.id)
            .update(map)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    fun getProductById(productId: String, onResult: (Product?) -> Unit) {
        firestore.collection("products")
            .document(productId)
            .get()
            .addOnSuccessListener { doc ->
                onResult(doc.toObject(Product::class.java)?.copy(id = doc.id))
            }
            .addOnFailureListener { onResult(null) }
    }

    fun getProductsByProvider(providerId: String, onResult: (List<Product>) -> Unit) {
        firestore.collection("products")
            .whereEqualTo("providerId", providerId)
            .get()
            .addOnSuccessListener { snap ->
                onResult(snap.documents.mapNotNull { it.toObject(Product::class.java)?.copy(id = it.id) })
            }
    }

    fun getFilteredProducts(categoria: String?, ciudad: String?, proveedorId: String?, onComplete: (List<Product>) -> Unit) {
        var q: Query = firestore.collection("products")
        categoria?.takeIf { it != "Todas" }?.let { q = q.whereEqualTo("category", it) }
        ciudad?.takeIf(String::isNotBlank)?.let { q = q.whereEqualTo("city", it.trim().lowercase()) }
        proveedorId?.takeIf(String::isNotBlank)?.let { q = q.whereEqualTo("providerId", it) }
        q.get()
            .addOnSuccessListener { snap ->
                onComplete(snap.documents.mapNotNull { it.toObject(Product::class.java)?.copy(id = it.id) })
            }
            .addOnFailureListener { onComplete(emptyList()) }
    }

    fun getProducts(onResult: (List<Product>) -> Unit) {
        firestore.collection("products")
            .get()
            .addOnSuccessListener { snap ->
                onResult(snap.documents.mapNotNull {
                    it.toObject(Product::class.java)?.copy(id = it.id)
                })
            }
    }

    /*** GEO ***/
    fun getUserById(uid: String, onResult: (User?) -> Unit) {
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val user = doc.toObject(User::class.java)
                    user?.id = doc.id // Asignamos el ID
                    onResult(user)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    fun fetchNearbyProviders(lat: Double, lng: Double, onResult: (List<User>) -> Unit) {
        firestore.collection("users")
            .whereEqualTo("role", "provider")
            .get()
            .addOnSuccessListener { snap ->
                val nearby = snap.documents.mapNotNull { doc ->
                    val ulat = doc.getDouble("lat")
                    val ulng = doc.getDouble("lng")
                    if (ulat != null && ulng != null) {
                        val distance = FloatArray(1)
                        Location.distanceBetween(lat, lng, ulat, ulng, distance)
                        if (distance[0] <= 20000) { // 20km radius
                            val user = doc.toObject(User::class.java)
                            user?.id = doc.id // Asignamos el ID
                            user
                        } else null
                    } else null
                }
                onResult(nearby)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }
}


