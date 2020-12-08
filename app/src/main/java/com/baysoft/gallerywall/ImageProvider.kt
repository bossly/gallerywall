package com.baysoft.gallerywall

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class PixabayResultItem(
        val imageURL: String,
        val tags: String
)

data class PixabayResult(
    val total: Int,
    val hits: List<PixabayResultItem>
)

interface ServiceApi {
    @GET("api")
    suspend fun loadPixabay(
        @Query("key") key: String,
        @Query("q") query: String,
        @Query("response_group") group: String = "high_resolution",
        @Query("min_width") width: Long = 1000,
        @Query("min_height") height: Long = 1000
    ): PixabayResult?
}

class ImageProvider {
    companion object {
        val serviceApi: ServiceApi
            get() {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://pixabay.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                return retrofit.create(ServiceApi::class.java)
            }
    }
}