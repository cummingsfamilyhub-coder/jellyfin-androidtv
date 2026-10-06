package org.jellyfin.androidtv.ui.preference

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.screen.UserPreferencesScreen

class PreferencesActivity : FragmentActivity(R.layout.fragment_content_view) {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val screen = intent.extras?.getString(EXTRA_SCREEN) ?: UserPreferencesScreen::class.qualifiedName
		val screenArgs = intent.extras?.getBundle(EXTRA_SCREEN_ARGS) ?: bundleOf()

		supportFragmentManager
			.beginTransaction()
			.replace(R.id.content_view, PreferencesFragment().apply {
				// Set screen
				arguments = bundleOf(
					PreferencesFragment.EXTRA_SCREEN to screen,
					PreferencesFragment.EXTRA_SCREEN_ARGS to screenArgs
				)
			}, FRAGMENT_TAG)
			.commit()

		if (intent.getBooleanExtra(EXTRA_VESPER_MOBILE_ENTRY, false)) {
			addVesperReturnControl()
		}
	}

	private fun addVesperReturnControl() {
		val root = findViewById<ViewGroup>(android.R.id.content) ?: return
		val button = TextView(this).apply {
			text = "←  Vesper"
			textSize = 15f
			setTextColor(Color.WHITE)
			gravity = Gravity.CENTER
			setPadding(dp(14), dp(9), dp(14), dp(9))
			background = GradientDrawable().apply {
				setColor(Color.parseColor("#E61A1C24"))
				cornerRadius = dp(18).toFloat()
				setStroke(dp(1), Color.parseColor("#665B47D8"))
			}
			setOnClickListener { finish() }
		}
		root.addView(
			button,
			FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT,
				Gravity.TOP or Gravity.START,
			).apply {
				leftMargin = dp(18)
				topMargin = dp(18)
			}
		)
	}

	private fun dp(value: Int): Int =
		(value * resources.displayMetrics.density).toInt()

	companion object {
		const val EXTRA_SCREEN = "screen"
		const val EXTRA_SCREEN_ARGS = "screen_args"
		const val EXTRA_VESPER_MOBILE_ENTRY = "vesper_mobile_entry"
		const val FRAGMENT_TAG = "PreferencesActivity"
	}
}

