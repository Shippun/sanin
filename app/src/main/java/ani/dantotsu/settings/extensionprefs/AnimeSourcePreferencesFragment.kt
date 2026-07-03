package ani.dantotsu.settings.extensionprefs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AnimeSourcePreferencesFragment : BottomSheetDialogFragment() {
    fun getInstance(id: Long, callback: () -> Unit): AnimeSourcePreferencesFragment {
        return this
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return View(requireContext())
    }
}
