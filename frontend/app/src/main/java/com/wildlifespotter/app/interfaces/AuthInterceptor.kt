package com.wildlifespotter.app.interfaces

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AuthInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // Obtain logged user
        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            try {
                val result = Tasks.await(user.getIdToken(false))
                val token = result.token

                if (!token.isNullOrEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}