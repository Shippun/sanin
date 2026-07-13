package ani.sanin.settings.extensionprefs

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource

class AnimeSourcePreferencesFragment : PreferenceFragmentCompat() {
    private var source: ConfigurableAnimeSource? = null
    private var onDismiss: (() -> Unit)? = null

    fun getInstance(source: ConfigurableAnimeSource, callback: () -> Unit): AnimeSourcePreferencesFragment {
        this.source = source
        this.onDismiss = callback
        return this
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        source?.setupPreferenceScreen(screen)
        preferenceScreen = screen
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRemoving) {
            onDismiss?.invoke()
        }
    }
}
