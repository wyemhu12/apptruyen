package com.personal.apptruyen.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("Exploratory test — gọi network thật, chỉ chạy thủ công")
class LacaTruyenApiTest {
    @Test
    fun testListChapters() {
        val client = OkHttpClient()
        val key = "T5Hr41U5jKTTrtUOXdYZnyx3wjZEKUoxv16Clwwu4D5zIbd0"
        val storyIdInt = 15309 // Example story ID, I'll need to find a real one
        val itemsPerPage = 10000

        val payloadStr =
            """{""" +
                """"id_story":"${CryptoJS.encrypt(storyIdInt.toString(), key)}",""" +
                """"page":"${CryptoJS.encrypt("1", key)}",""" +
                """"items_per_page":"${CryptoJS.encrypt(itemsPerPage.toString(), key)}",""" +
                """"order":"${CryptoJS.encrypt("asc", key)}",""" +
                """"chapter_reading":"${CryptoJS.encrypt("", key)}"""" +
                """}"""

        val req =
            Request
                .Builder()
                .url("https://www.lacatruyen.buzz/api/chapters/list-chapters")
                .post(payloadStr.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.lacatruyen.buzz")
                .header("Referer", "https://www.lacatruyen.buzz/story/tien-nghich")
                .build()

        val res = client.newCall(req).execute()
        val bodyStr = res.body?.string() ?: ""
        println("Code: ${res.code}")
        println("Body: $bodyStr")
    }
}
