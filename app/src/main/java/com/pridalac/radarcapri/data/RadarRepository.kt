package com.pridalac.radarcapri.data

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Base de datos de radares en local.
 *
 * - Descargada: `filesDir/radares.json` (botón "Descargar radares").
 * - De respaldo: `assets/radares.json`, incluida en la APK, para el primer uso sin conexión.
 *
 * El fichero lo genera `tools/generar_radares.py` (DGT + Trànsit + OpenStreetMap).
 */
class RadarRepository(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Marca que cambia cuando se descarga una base nueva (0 = solo la de la APK). */
    fun stamp(): Long = if (file.exists()) file.lastModified() else 0L

    /** Fecha de la última descarga, o null si solo se usa la base incluida en la APK. */
    fun lastDownload(): Long? = if (file.exists()) file.lastModified() else null

    suspend fun loadLocal(): List<SpeedCamera> = withContext(Dispatchers.IO) {
        try {
            val stream: InputStream = if (file.exists()) file.inputStream()
            else appContext.assets.open(FILE_NAME)
            stream.use { parse(it) }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo leer la base de radares", e)
            emptyList()
        }
    }

    /** Descarga la base, la valida y la sustituye de forma atómica. Devuelve el nº de radares. */
    suspend fun download(): Result<Int> = withContext(Dispatchers.IO) {
        val tmp = File(appContext.filesDir, "$FILE_NAME.tmp")
        try {
            val request = Request.Builder().url(DATA_URL).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("Respuesta vacía")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            val count = tmp.inputStream().use { parse(it) }.size
            if (count < MIN_VALID_COUNT) error("Base incompleta ($count radares)")
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Descarga fallida", e)
            tmp.delete()
            Result.failure(e)
        }
    }

    private fun parse(stream: InputStream): List<SpeedCamera> {
        val root = JsonParser.parseReader(stream.reader(Charsets.UTF_8)).asJsonObject
        val list = ArrayList<SpeedCamera>()
        for (el in root.getAsJsonArray("radares")) {
            val o = el.asJsonObject
            val lat = o.get("a")?.asDouble ?: continue
            val lng = o.get("o")?.asDouble ?: continue
            list.add(
                SpeedCamera(
                    id = o.get("i")?.asString ?: "$lat,$lng",
                    name = o.get("n")?.asString?.takeIf { it.isNotBlank() } ?: "Radar",
                    lat = lat,
                    lng = lng,
                    type = o.get("t")?.asString ?: "fijo",
                    commune = o.get("c")?.asString ?: "",
                    vitesse = o.get("v")?.asInt ?: 0,
                    azimut = o.get("d")?.asFloat ?: -1f
                )
            )
        }
        return list
    }

    companion object {
        private const val TAG = "RadarRepository"
        private const val FILE_NAME = "radares.json"
        private const val MIN_VALID_COUNT = 500
        const val DATA_URL = "https://raw.githubusercontent.com/krinda3/RadarCapri/main/radares.json"
    }
}
