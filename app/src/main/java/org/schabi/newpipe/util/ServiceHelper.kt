/*
 * SPDX-FileCopyrightText: 2018-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.ktx.getStringSafe

object ServiceHelper {
    private val YOUTUBE_SERVICE: StreamingService = ServiceList.YouTube

    @JvmStatic
    fun getSupportedServices(): List<StreamingService> = listOf(YOUTUBE_SERVICE)

    @JvmStatic
    fun isSupportedServiceId(serviceId: Int): Boolean {
        return serviceId == YOUTUBE_SERVICE.serviceId
    }

    @JvmStatic
    @DrawableRes
    fun getIcon(serviceId: Int): Int {
        return if (isSupportedServiceId(serviceId)) R.drawable.ic_smart_display
        else R.drawable.ic_circle
    }

    @JvmStatic
    fun getTranslatedFilterString(filter: String, context: Context): String {
        return when (filter) {
            "all" -> context.getString(R.string.all)
            "videos", "sepia_videos", "music_videos" -> context.getString(R.string.videos_string)
            "channels" -> context.getString(R.string.channels)
            "playlists", "music_playlists" -> context.getString(R.string.playlists)
            "tracks" -> context.getString(R.string.tracks)
            "users" -> context.getString(R.string.users)
            "conferences" -> context.getString(R.string.conferences)
            "events" -> context.getString(R.string.events)
            "music_songs" -> context.getString(R.string.songs)
            "music_albums" -> context.getString(R.string.albums)
            "music_artists" -> context.getString(R.string.artists)
            else -> filter
        }
    }

    /**
     * Get a resource string with instructions for importing subscriptions for each service.
     *
     * @param serviceId service to get the instructions for
     * @return the string resource containing the instructions or -1 if the service don't support it
     */
    @JvmStatic
    @StringRes
    fun getImportInstructions(serviceId: Int): Int {
        return if (isSupportedServiceId(serviceId)) R.string.import_youtube_instructions else -1
    }

    /**
     * For services that support importing from a channel url, return a hint that will
     * be used in the EditText that the user will type in his channel url.
     *
     * @param serviceId service to get the hint for
     * @return the hint's string resource or -1 if the service don't support it
     */
    @JvmStatic
    @StringRes
    fun getImportInstructionsHint(serviceId: Int): Int {
        return -1
    }

    @JvmStatic
    fun getSelectedServiceId(context: Context): Int {
        return (getSelectedService(context) ?: YOUTUBE_SERVICE).serviceId
    }

    @JvmStatic
    fun getSelectedService(context: Context): StreamingService? {
        val serviceName: String = PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSafe(
                context.getString(R.string.current_service_key),
                context.getString(R.string.default_service_value)
            )

        if (serviceName != YOUTUBE_SERVICE.serviceInfo.name) {
            setSelectedServicePreferences(context, YOUTUBE_SERVICE.serviceInfo.name)
        }
        return YOUTUBE_SERVICE
    }

    @JvmStatic
    fun getNameOfServiceById(serviceId: Int): String {
        return if (isSupportedServiceId(serviceId)) YOUTUBE_SERVICE.serviceInfo.name else "<unknown>"
    }

    /**
     * @param serviceId the id of the service
     * @return the service corresponding to the provided id
     * @throws java.util.NoSuchElementException if there is no service with the provided id
     */
    @JvmStatic
    fun getServiceById(serviceId: Int): StreamingService {
        if (!isSupportedServiceId(serviceId)) {
            throw NoSuchElementException("Unsupported service id: $serviceId")
        }
        return YOUTUBE_SERVICE
    }

    @JvmStatic
    fun setSelectedServiceId(context: Context, serviceId: Int) {
        setSelectedServicePreferences(context, YOUTUBE_SERVICE.serviceInfo.name)
    }

    private fun setSelectedServicePreferences(context: Context, serviceName: String?) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit { putString(context.getString(R.string.current_service_key), serviceName) }
    }

    @JvmStatic
    fun getCacheExpirationMillis(serviceId: Int): Long {
        return 3_600_000L
    }
}
