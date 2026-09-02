package com.example.redbook.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.nio.charset.Charset
import kotlin.coroutines.resume

/**
 * IP 归属地解析：双方案获取"省"名（如"湖北"）。
 * 1. 设备定位（模拟器/真机虚拟定位）→ 逆地理编码取省级行政区；
 * 2. 公网 IP 归属接口返回中文省份。
 * 定位与公网 IP 并行竞速，谁先出结果用谁；全部失败返回 null。
 */
object IpLocationProvider {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    /** 进程内缓存 */
    @Volatile
    var cachedProvince: String? = null

    /** 解析省份；成功写入缓存并返回 */
    suspend fun resolveProvince(context: Context): String? {
        cachedProvince?.let { return it }
        val result: String? = try {
            coroutineScope {
                val locationJob = async { provinceByLocation(context.applicationContext) }
                val ipJob = async { provinceByPublicIp() }
                val loc = withTimeoutOrNull(7000) { locationJob.await() }
                val ip = withTimeoutOrNull(7000) { ipJob.await() }
                loc?.takeIf { it.isNotBlank() } ?: ip?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) { null }
        val cleaned = cleanProvince(result)
        if (cleaned.isNotBlank()) {
            cachedProvince = cleaned
        }
        return if (cleaned.isBlank()) null else cleaned
    }

    // ---------- 方案一：设备定位（虚拟定位在此生效） ----------

    @SuppressLint("MissingPermission")
    private suspend fun provinceByLocation(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!hasLocationPermission(context)) return@withContext null
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
                if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return@withContext null
                val loc: Location? = try {
                    lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        ?: requestSingleFix(lm)
                } catch (_: Exception) { null }
                if (loc == null) return@withContext null
                val d = android.util.Log.d("RedBookIp", "location fix: lat=${loc.latitude} lng=${loc.longitude}")
                reverseGeocodeProvince(context, loc.latitude, loc.longitude)
            } catch (_: Exception) { null }
        }
    }

    /** 请求一次最新定位（模拟器虚拟定位冷启动后 lastKnown 常为空，需主动请求） */
    @SuppressLint("MissingPermission")
    private suspend fun requestSingleFix(lm: LocationManager): Location? {
        return try {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                val activeProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
                if (activeProviders.isEmpty()) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                var done = false
                fun cleanup(l: android.location.LocationListener) {
                    if (done) return
                    done = true
                    runCatching { lm.removeUpdates(l) }
                }
                val locationListener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) { cont.resume(location); cleanup(this) }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) { }
                    override fun onProviderEnabled(provider: String) { }
                    override fun onProviderDisabled(provider: String) { }
                }
                val timeout = Runnable {
                    if (cont.isActive) { cont.resume(null); cleanup(locationListener) }
                }
                try {
                    activeProviders.forEach {
                        lm.requestLocationUpdates(it, 0L, 0f, locationListener, android.os.Looper.getMainLooper())
                    }
                } catch (_: Exception) {
                    if (cont.isActive) { cont.resume(null); cleanup(locationListener) }
                    return@suspendCancellableCoroutine
                }
                handler.postDelayed(timeout, 6000)
                cont.invokeOnCancellation { cleanup(locationListener) }
            }
        } catch (_: Exception) { null }
    }

    /** 逆地理编码：先系统 Geocoder，失败(无 Google 服务的模拟器常见)再走 Nominatim */
    private suspend fun reverseGeocodeProvince(context: Context, lat: Double, lng: Double): String? {
        systemGeocodeProvince(context, lat, lng)?.let { return it }
        return nominatimProvince(lat, lng)
    }

    private suspend fun systemGeocodeProvince(context: Context, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val gc = Geocoder(context, Locale.CHINA)
                val addrs = gc.getFromLocation(lat, lng, 1)
                if (addrs.isNullOrEmpty()) return@withContext null
                val admin = addrs[0].adminArea ?: return@withContext null
                android.util.Log.d("RedBookIp", "geocoder adminArea=$admin")
                admin
            } catch (e: Exception) {
                android.util.Log.d("RedBookIp", "geocoder failed: ${e.message}")
                null
            }
        }

    /** Nominatim 逆地理编码（https，无需 key，不依赖 Google 服务）；返回省/直辖市名 */
    private suspend fun nominatimProvince(lat: Double, lng: Double): String? {
        val body = get("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lng&accept-language=zh-CN")
            ?: return null
        return try {
            val addr = JSONObject(body).optJSONObject("address") ?: return null
            addr.optString("state", "").ifBlank {
                addr.optString("province", "").ifBlank { addr.optString("region", "") }
            }
        } catch (_: Exception) { null }
    }

    // ---------- 方案二：公网 IP 归属接口 ----------

    private suspend fun provinceByPublicIp(): String? {
        // 多个免费接口并发竞速，取最先成功的中文结果
        return try {
            coroutineScope {
                val jobs = listOf(
                    async { ipipProvince() },
                    async { voreProvince() }
                )
                for (job in jobs) {
                    val r = withTimeoutOrNull(5000) { job.await() }
                    val cleaned = cleanProvince(r)
                    if (cleaned.isNotBlank()) return@coroutineScope cleaned
                }
                null
            }
        } catch (_: Exception) { null }
    }

    /** https://myip.ipip.net → 纯文本 "当前 IP：117.154.155.37  来自于：中国 湖北 襄阳  移动" */
    private suspend fun ipipProvince(): String? {
        val body = get("https://myip.ipip.net") ?: return null
        android.util.Log.d("RedBookIp", "ipip raw=$body")
        // 取 "来自于：" 之后的文本
        val idx = body.indexOf("来自于")
        return if (idx >= 0) body.substring(idx) else null
    }

    /** https://api.vore.top/api/IPdata → data.location "中国湖北省武汉市" */
    private suspend fun voreProvince(): String? {
        val body = get("https://api.vore.top/api/IPdata") ?: return null
        return try {
            val data = JSONObject(body).optJSONObject("data")
            data?.optString("location", "")
        } catch (_: Exception) { null }
    }

    private suspend fun get(url: String): String? = getBytes(url)?.let { String(it, Charsets.UTF_8) }

    private suspend fun getBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.bytes()
            }
        } catch (e: Exception) {
            android.util.Log.d("RedBookIp", "request failed $url: ${e.message}")
            null
        }
    }

    // ---------- 工具 ----------

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = android.Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = android.Manifest.permission.ACCESS_COARSE_LOCATION
        return androidx.core.content.ContextCompat.checkSelfPermission(context, fine) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, coarse) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** 是否开启了系统定位开关（模拟器虚拟定位需打开） */
    fun isLocationEnabled(context: Context): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { false }
    }

    /** 清洗为省份短名：湖北省→湖北；中国湖北省武汉市→湖北；广西壮族自治区→广西；北京→北京 */
    fun cleanProvince(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.trim()
        if (s.isEmpty()) return ""
        // 去掉国家前缀
        s = s.replace(Regex("^(中国|中华人民共和国|CHINA|China|CN)\\s*"), "").trim()
        val province = extractRegion(s)
        val result = province
            .replace("壮族自治区", "")
            .replace("回族自治区", "")
            .replace("维吾尔自治区", "")
            .replace("自治区", "")
            .replace("特别行政区", "")
            .replace("省", "")
            .replace("市", "")
            .trim()
        // 只接受中文结果，英文/乱码一律丢弃
        return if (result.any { it in '\u4e00'..'\u9fff' }) result else ""
    }

    /** 从完整地址/归属地串中提取一级行政区名（省级） */
    private fun extractRegion(s: String): String {
        val text = s.trimStart('.', '，', ',', ' ', '：', ':')
        // 完整形式优先（含 省/自治区 等后缀）
        val fullForms = listOf(
            "新疆维吾尔自治区", "内蒙古自治区", "广西壮族自治区", "宁夏回族自治区",
            "西藏自治区", "香港特别行政区", "澳门特别行政区",
            "黑龙江省", "河北省", "吉林省", "辽宁省", "山东省", "山西省", "河南省",
            "陕西省", "甘肃省", "青海省", "四川省", "贵州省", "云南省", "湖南省", "湖北省",
            "安徽省", "江苏省", "浙江省", "江西省", "福建省", "广东省", "海南省", "台湾省"
        )
        for (k in fullForms) {
            if (text.contains(k)) return k
        }
        // 裸省名/自治区短名（如 ipip 文本 "中国 湖北 襄阳"）
        val shortForms = listOf(
            "黑龙江", "内蒙古", "广西", "新疆", "宁夏", "西藏", "香港", "澳门",
            "河北", "吉林", "辽宁", "山东", "山西", "河南", "陕西", "甘肃", "青海",
            "四川", "贵州", "云南", "湖南", "湖北", "安徽", "江苏", "浙江", "江西",
            "福建", "广东", "海南", "台湾", "北京", "天津", "上海", "重庆"
        )
        for (k in shortForms) {
            if (text.contains(k)) return k
        }
        // 通用规则：取第一个 "省/自治区/特别行政区" 之前的文本
        val m = Regex("([\\u4e00-\\u9fa5]{2,6}?)(?:省|自治区|特别行政区)").find(text)
        if (m != null) return m.groupValues[1]
        // 直辖市兜底：xxx市
        val city = Regex("([\\u4e00-\\u9fa5]{2,4}?)(?:市)").find(text)
        return city?.groupValues?.get(1) ?: text.take(4)
    }

    /** 跳转系统定位设置（供界面引导用户开启虚拟定位/定位） */
    fun openLocationSettings(context: Context) {
        try {
            val intent = android.content.Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}
