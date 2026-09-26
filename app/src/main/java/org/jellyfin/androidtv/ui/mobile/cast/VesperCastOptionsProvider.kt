package org.jellyfin.androidtv.ui.mobile.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

class VesperCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(
                receiverApplicationId
                    ?: JELLYFIN_STABLE_RECEIVER_ID
            )
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null

    companion object {
        private const val JELLYFIN_STABLE_RECEIVER_ID = "F007D354"

        @Volatile
        private var receiverApplicationId: String? = null

        fun setReceiverApplicationId(value: String?) {
            receiverApplicationId = value?.takeIf { it.isNotBlank() }
        }
    }
}
