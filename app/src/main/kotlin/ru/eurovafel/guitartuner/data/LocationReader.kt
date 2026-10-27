package ru.eurovafel.guitartuner.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Последнее известное или текущее местоположение устройства.
 *
 * Google Play Services не нужен: хватает системного LocationManager.
 * Свежую точку запрашиваем только на Android 11+, на старых системах
 * довольствуемся последней известной — карте этого достаточно.
 */
class LocationReader(private val context: Context) {

    fun hasPermission(): Boolean =
        check(Manifest.permission.ACCESS_FINE_LOCATION) ||
            check(Manifest.permission.ACCESS_COARSE_LOCATION)

    suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        val manager = runCatching {
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }.getOrNull() ?: return null

        val lastKnown = lastKnown(manager)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return lastKnown

        // Сеть отвечает быстрее GPS и обычно доступна в помещении.
        val provider = when {
            enabled(manager, LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            enabled(manager, LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return lastKnown
        }

        val fresh = withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) {
            requestCurrent(manager, provider)
        }
        return fresh ?: lastKnown
    }

    private fun lastKnown(manager: LocationManager): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun requestCurrent(
        manager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        manager.getCurrentLocation(
            provider,
            signal,
            ContextCompat.getMainExecutor(context),
        ) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }

    private fun enabled(manager: LocationManager, provider: String): Boolean =
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)

    private fun check(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 8_000L
    }
}
