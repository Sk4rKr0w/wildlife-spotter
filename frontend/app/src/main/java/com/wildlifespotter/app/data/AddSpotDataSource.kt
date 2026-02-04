package com.wildlifespotter.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.Geocoder
import android.os.Build
import android.provider.MediaStore
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.wildlifespotter.app.interfaces.IdentifyTaxonomy
import com.wildlifespotter.app.interfaces.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object AddSpotDataSource {
    data class LocationResult(
        val latitude: Double,
        val longitude: Double,
        val name: String,
        val isValid: Boolean
    )

    data class UploadResult(
        val success: Boolean,
        val message: String
    )

    suspend fun compressImage(context: Context, uri: android.net.Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            compressBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun compressBitmap(bitmap: Bitmap, maxSize: Int = 1024, quality: Int = 92): ByteArray {
        val ratio = maxOf(bitmap.width, bitmap.height).toFloat() / maxSize
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width / ratio).toInt(),
            (bitmap.height / ratio).toInt(),
            true
        )
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return baos.toByteArray()
    }

    @SuppressLint("MissingPermission")
    suspend fun getFastLocation(context: Context): LocationResult {
        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            val last = client.lastLocation.await()
                ?: client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            if (last != null) {
                val name = reverseGeocode(context, last.latitude, last.longitude) ?: "Unknown Location"
                val isValid = last.latitude != 0.0 && last.longitude != 0.0 &&
                    !name.contains("unavailable", ignoreCase = true) &&
                    !name.contains("Error", ignoreCase = true)
                LocationResult(last.latitude, last.longitude, name, isValid)
            } else {
                LocationResult(0.0, 0.0, "Location unavailable", false)
            }
        } catch (e: Exception) {
            LocationResult(0.0, 0.0, "Error", false)
        }
    }

    private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
                addresses?.firstOrNull()?.let {
                    "${it.locality ?: ""}-${it.subLocality ?: it.subAdminArea ?: ""}"
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun uploadSpotWithBytes(
        bytes: ByteArray,
        species: String,
        description: String,
        latitude: Double,
        longitude: Double,
        locationName: String,
        azimuth: Float
    ): UploadResult {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        return try {
            val imagePart = MultipartBody.Part.createFormData(
                "image",
                "upload.jpg",
                bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            val uploadRes = RetrofitInstance.api.uploadImage(imagePart)
            val imageId = uploadRes.id
            val userId = auth.currentUser?.uid

            val countryCode = if (userId != null) {
                db.collection("users").document(userId).get().await().getString("country") ?: "ITA"
            } else {
                "ITA"
            }

            val identifyRes = try {
                RetrofitInstance.api.identifyImage(imageId, countryCode)
            } catch (e: Exception) {
                null
            }
            val annotation = identifyRes?.annotations?.firstOrNull()
            val label = annotation?.label?.takeIf { it.isNotBlank() }
                ?: (if (species.isBlank()) "Unknown Species" else species)

            val geohash = GeoFireUtils.getGeoHashForLocation(GeoLocation(latitude, longitude))

            val todayKey = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            val dailySteps = if (userId != null) {
                db.collection("users")
                    .document(userId)
                    .collection("steps")
                    .document(todayKey)
                    .get()
                    .await()
                    .getLong("dailySteps") ?: 0L
            } else {
                0L
            }

            val spotData = hashMapOf(
                "species" to mapOf("label" to label, "taxonomy" to taxonomyToMap(annotation?.taxonomy)),
                "description" to description,
                "latitude" to latitude,
                "longitude" to longitude,
                "location_name" to locationName,
                "geohash" to geohash,
                "timestamp" to FieldValue.serverTimestamp(),
                "image_id" to imageId,
                "user_id" to (userId ?: "anonymous"),
                "daily_steps" to dailySteps,
                "sensor_data" to mapOf("compass_azimuth" to azimuth)
            )

            db.collection("spots").add(spotData).await()
            if (userId != null) {
                db.collection("users").document(userId)
                    .set(
                        mapOf("totalSpots" to FieldValue.increment(1)),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()
            }
            UploadResult(
                true,
                if (annotation?.label != null) {
                    "Identified: ${annotation.label.replaceFirstChar { it.uppercase() }}"
                } else {
                    "Spot added!"
                }
            )
        } catch (e: Exception) {
            UploadResult(false, "Error: ${e.message}")
        }
    }

    private fun taxonomyToMap(taxonomy: IdentifyTaxonomy?): Map<String, Any?> {
        if (taxonomy == null) return emptyMap()
        return mapOf(
            "id" to taxonomy.id,
            "class" to taxonomy.className,
            "order" to taxonomy.order,
            "family" to taxonomy.family,
            "genus" to taxonomy.genus,
            "species" to taxonomy.species
        )
    }
}
