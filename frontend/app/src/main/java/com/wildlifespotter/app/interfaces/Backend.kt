package com.wildlifespotter.app.interfaces

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.google.gson.annotations.SerializedName
import retrofit2.http.DELETE

data class UploadResponse(val id: String)

interface BackendApi {
    @Multipart
    @POST("images")
    suspend fun uploadImage(@Part image: MultipartBody.Part): UploadResponse

    @GET("images/{id}")
    suspend fun getImage(@Path("id") id: String): ResponseBody

    @GET("images/{id}/identify")
    suspend fun identifyImage(
        @Path("id") id: String,
        @Query("country") country: String? = null
    ): IdentifyResponse

    @DELETE("images/{id}")
    suspend fun deleteImage(@Path("id") id: String)
}

object RetrofitInstance {
    const val BASE_URL = "https://api.widlifespotter.app/"

    private val client by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = AuthInterceptor()

        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val api: BackendApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendApi::class.java)
    }

    fun imageUrl(id: String): String = "${BASE_URL}images/$id"
}

data class IdentifyRequest(
    val country: String? = null,
    val threshold: Double? = null
)

data class IdentifyResponse(
    val annotations: List<IdentifyAnnotation>? = null,
    val info: IdentifyInfo? = null
)

data class IdentifyAnnotation(
    val id: Int? = null,
    val bbox: List<Double>? = null,
    val score: Double? = null,
    val label: String? = null,
    val taxonomy: IdentifyTaxonomy? = null
)

data class IdentifyTaxonomy(
    val id: String? = null,
    @SerializedName("class") val className: String? = null,
    val order: String? = null,
    val family: String? = null,
    val genus: String? = null,
    val species: String? = null
)

data class IdentifyInfo(
    val processing_time_ms: Int? = null,
    val model_version: String? = null,
    val country_processed: String? = null,
    val threshold_applied: Double? = null
)
