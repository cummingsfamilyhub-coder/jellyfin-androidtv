package org.jellyfin.androidtv.ui.mobile.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

class VesperCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(
                receiverApplicationId
                    ?: CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            )
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null

    companion object {
        @Volatile
        private var receiverApplicationId: String? = null

        fun setReceiverApplicationId(value: String?) {
            receiverApplicationId = value?.takeIf { it.isNotBlank() }
        }
    }
}
