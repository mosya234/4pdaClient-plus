package ru.slartus.http


import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.support.v4.util.Pair
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.MimeTypeMap.getFileExtensionFromUrl
import okhttp3.*
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.Buffer
import java.io.File
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy.ACCEPT_ALL
import java.util.*
import java.util.concurrent.TimeUnit
import okhttp3.RequestBody.Companion.asRequestBody
import java.net.HttpCookie
import java.net.URI
import java.nio.charset.Charset

@Suppress("unused")
/*
 * Created by slartus on 25.01.2015.
 */
class Http private constructor(context: Context, appName: String, appVersion: String) {

    companion object {
        const val TAG = "Http"
        private var INSTANCE: Http? = null
        private const val FULL_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.100 Safari/537.36"
        fun init(context: Context, appName: String, appVersion: String) {
            INSTANCE = Http(context, appName, appVersion)
        }

        val instance by lazy { INSTANCE!! }

        @Suppress("DEPRECATION")
        fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val netInfo = cm.activeNetworkInfo
            return netInfo != null && netInfo.isConnectedOrConnecting
        }

        private fun getMimeType(url: String): String? {
            var type: String? = null
            val extension = getFileExtensionFromUrl(url)
            if (extension != null) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            }
            return type
        }
    }

    private var client: OkHttpClient
    var cookieStore: PersistentCookieStore = PersistentCookieStore.getInstance(context)

    init {
        val cookieHandler = CookieManager(cookieStore, ACCEPT_ALL)

        val builder = OkHttpClient.Builder()
                .cookieJar(JavaNetCookieJar(cookieHandler))
                .retryOnConnectionFailure(true)
                .connectTimeout(15, TimeUnit.SECONDS) // connect timeout
                .writeTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)

//        if (BuildConfig.DEBUG)
//            builder.addInterceptor(DebugLoggingInterceptor())
//        else
//            builder.addInterceptor(LoggingInterceptor())

        client = builder
                .build()    // socket timeout
    }

    private val userAgent by lazy {
        String.format(Locale.getDefault(),
                "%s/%s (Android %s; %s; %s %s; %s)",
                appName,
                appVersion,
                Build.VERSION.RELEASE,
                Build.MODEL,
                Build.BRAND,
                Build.DEVICE,
                Locale.getDefault().language)
    }


    private fun prepareUrl(url: String) = url.replace(Regex("^//4pda\\.ru"), "http://4pda.ru")

    private fun buildRequestHeaders(userAgent: String = this.userAgent): Headers {
        val headersBuilder = Headers.Builder()
        headersBuilder.add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3")
        // headersBuilder.add("Accept-Encoding", "gzip, deflate")
        headersBuilder.add("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7,vi;q=0.6,bg;q=0.5")
        headersBuilder.add("User-Agent", userAgent)
        return headersBuilder.build()
    }

    fun response(url: String) = response(url, false)
    private fun response(url: String, desktopVersion: Boolean = false): Response {
        val request = Request.Builder()
                .headers(buildRequestHeaders(if (desktopVersion) FULL_USER_AGENT else userAgent))
                .url(prepareUrl(url))
                .build()
        try {
            if (desktopVersion)
                setCookieDeskVer(true)
            return client.newCall(request).execute()
        } finally {
            if (desktopVersion)
                setCookieDeskVer(false)
        }
    }

    private fun setCookieDeskVer(deskVer: Boolean) {
        val uri = URI.create("http://4pda.ru/")
        cookieStore.cookies.filter { it.name == "deskver" }.forEach {
            cookieStore.remove(uri, it)
        }
        cookieStore.add(uri, HttpCookie("deskver", if (deskVer) "1" else "0"))
    }

    fun performGet(url: String): AppResponse {
        val response = response(url)
        val body = response.body?.string()
        return AppResponse(url, response.request.url.toString(), body)
    }

    fun performGetFull(url: String): AppResponse {
        val response = response(url, true)
        val body = response.body?.string()
        return AppResponse(url, response.request.url.toString(), body)
    }

    fun postMultipart(url: String, values: List<Pair<String, String>>): AppResponse {
        // Use the imgur image upload API as documented at https://api.imgur.com/endpoints/image
        val formBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
        for (nameValuePair in values) {
            formBuilder.addFormDataPart(nameValuePair.first!!, nameValuePair.second!!)
        }

        val requestBody = formBuilder.build()

        val request = Request.Builder()
                .headers(buildRequestHeaders(userAgent))
                .url(prepareUrl(url))
                .post(requestBody)
                .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw HttpException("Unexpected code $response")

            val body = response.body?.string()
            return AppResponse(url, response.request.url.toString(), body)
        } catch (ex: IOException) {
            throw HttpException(ex)
        }
    }

    @Throws(IOException::class)
    @JvmOverloads
    fun performPost(url: String, values: List<Pair<String, String>> = ArrayList()): AppResponse {
        val formBuilder = FormBody.Builder(Charset.forName("windows-1251"))
        values
                .filter { it.second != null }
                .forEach { formBuilder.add(it.first!!, it.second!!) }

        val formBody = formBuilder.build()

        Log.i(TAG, "post: $url")
        val request = Request.Builder()
                .headers(buildRequestHeaders(userAgent))
                .url(prepareUrl(url))
                .cacheControl(CacheControl.FORCE_NETWORK)
                .post(formBody)
                .build()


        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw HttpException("Unexpected code $response")

            val body = response.body?.string()
            return AppResponse(url, response.request.url.toString(), body)
        } catch (ex: IOException) {
            throw HttpException(ex)
        }

    }


    fun uploadFile(url: String, fileName: String, filePath: String, fileFormDataName: String,
                   formDataParts: List<Pair<String, String>> = emptyList(),
                   progressListener: CountingFileRequestBody.ProgressListener? = null): AppResponse {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)


        val file = File(filePath)
        val totalSize = file.length()

        val mediaType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(getFileExtensionFromUrl(file.toString()))?.toMediaTypeOrNull()


        if (progressListener != null) {
            builder.addPart(headersOf("Content-Disposition", "form-data; name=\"$fileFormDataName\"; filename=\"$fileName\""),
                    CountingFileRequestBody(file, mediaType) { num ->
                        val progress = num.toFloat() / totalSize.toFloat() * 100.0
                        progressListener.transferred(progress.toLong())
                    })
        } else {
            builder.addFormDataPart(fileFormDataName, fileName, file.asRequestBody(mediaType))
        }

        formDataParts.filter { it.first != null && it.second != null }.forEach {
            builder.addFormDataPart(it.first!!, it.second!!)
        }

        val requestBody = builder.build()
        val request = Request.Builder()
                .headers(buildRequestHeaders(userAgent))
                .url(prepareUrl(url))
                .post(requestBody)
                .build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw HttpException("Unexpected code $response")

            val body = response.body?.string()
            return AppResponse(url, response.request.url.toString(), body)
        } catch (ex: IOException) {
            throw HttpException(ex)
        }
    }

    private class LoggingInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            Log.i("OkHttp", request.url.toString())

            return chain.proceed(request)
        }
    }

    private class DebugLoggingInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            val t1 = System.nanoTime()
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            val requestBody = buffer.readUtf8()
            Log.d("OkHttp", String.format("Sending response %s on %s%n%s body: %s",
                    request.url, chain.connection(), request.headers, requestBody))

            val response = chain.proceed(request)

            val t2 = System.nanoTime()



            Log.d("OkHttp", String.format("Received response for %s in %.1fms%n%s",
                    response.request.url, (t2 - t1) / 1e6, response.headers))

            return response
        }
    }
}
