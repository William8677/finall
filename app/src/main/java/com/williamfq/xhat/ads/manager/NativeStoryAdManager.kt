package com.williamfq.xhat.ads.manager

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.VideoOptions
import com.williamfq.xhat.core.config.AdMobConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeStoryAdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var lastAdShowTime: Long = 0
    private var adsShownInSession = 0

    private val _currentNativeAd = MutableStateFlow<NativeAd?>(null)
    val currentNativeAd: StateFlow<NativeAd?> = _currentNativeAd

    private val _isAdLoading = MutableStateFlow(false)
    val isAdLoading: StateFlow<Boolean> = _isAdLoading

    private val _adError = MutableStateFlow<String?>(null)
    val adError: StateFlow<String?> = _adError

    init {
        preloadNextAd()
    }

    suspend fun isAdAvailable(): Boolean {
        return _currentNativeAd.value != null && !_isAdLoading.value
    }

    fun shouldShowAd(storyIndex: Int): Boolean {
        val currentTime = System.currentTimeMillis()
        return storyIndex > 0 &&
                storyIndex % AdMobConfig.STORIES_BETWEEN_ADS == 0 &&
                adsShownInSession < AdMobConfig.MAX_ADS_PER_SESSION &&
                currentTime - lastAdShowTime >= AdMobConfig.MIN_TIME_BETWEEN_ADS_MS &&
                _currentNativeAd.value != null
    }

    fun preloadNextAd() {
        if (_isAdLoading.value) return

        _isAdLoading.value = true
        _adError.value = null

        try {
            val adLoader = AdLoader.Builder(context, AdMobConfig.NATIVE_STORY_AD_UNIT_ID)
                .forNativeAd { nativeAd ->
                    _currentNativeAd.value = nativeAd
                    _isAdLoading.value = false
                    Timber.d("Anuncio nativo cargado exitosamente")
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        _isAdLoading.value = false
                        _adError.value = "Error al cargar anuncio: ${loadAdError.message}"
                        Timber.e("Error al cargar anuncio: ${loadAdError.message}")
                        preloadNextAd() // Reintentar carga
                    }

                    override fun onAdClosed() {
                        preloadNextAd()
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_PORTRAIT)
                        .setVideoOptions(
                            VideoOptions.Builder().setStartMuted(true).build()
                        )
                        .setRequestCustomMuteThisAd(true)
                        .build()
                )
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            _isAdLoading.value = false
            _adError.value = "Excepción al cargar anuncio: ${e.message}"
            Timber.e(e, "Excepción al cargar anuncio")
        }
    }

    fun markAdAsShown() {
        lastAdShowTime = System.currentTimeMillis()
        adsShownInSession++
        _currentNativeAd.value?.destroy()
        _currentNativeAd.value = null
        preloadNextAd()
    }

    fun resetSession() {
        adsShownInSession = 0
        lastAdShowTime = 0
        _currentNativeAd.value?.destroy()
        _currentNativeAd.value = null
        _isAdLoading.value = false
        _adError.value = null
        preloadNextAd()
    }

    fun release() {
        _currentNativeAd.value?.destroy()
        _currentNativeAd.value = null
        _isAdLoading.value = false
        _adError.value = null
    }
}