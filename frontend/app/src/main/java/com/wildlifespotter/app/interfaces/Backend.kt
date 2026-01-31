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
import java.util.concurrent.TimeUnit

data class UploadResponse(val id: String)

interface BackendApi {
    @Multipart
    @POST("images")
    suspend fun uploadImage(@Part image: MultipartBody.Part): UploadResponse

    @GET("images/{id}")
    suspend fun getImage(@Path("id") id: String): ResponseBody
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
