package ani.sanin.extension.cloudstream

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import ani.sanin.R
import ani.sanin.settings.SearchQueryHandler
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class CloudstreamExtensionsFragment : Fragment(), SearchQueryHandler {

    private var currentPage = 0
    private var lastQuery: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_cloudstream_extensions, container, false)
        val tabLayout = view.findViewById<TabLayout>(R.id.cloudstreamTabLayout)
        val viewPager = view.findViewById<ViewPager2>(R.id.cloudstreamViewPager)

        viewPager.adapter = object : FragmentStateAdapter(childFragmentManager, lifecycle) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> CloudstreamInstalledFragment()
                1 -> CloudstreamAvailableFragment()
                else -> CloudstreamInstalledFragment()
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                lastQuery?.let { forwardQuery(it) }
            }
        })

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Installed"
                1 -> "Available"
                else -> null
            }
        }.attach()

        return view
    }

    override fun updateContentBasedOnQuery(query: String?) {
        lastQuery = query
        forwardQuery(query ?: "")
    }

    override fun notifyDataChanged() {}

    private fun forwardQuery(query: String) {
        val tag = "f$currentPage"
        val frag = childFragmentManager.findFragmentByTag(tag)
        if (frag is SearchQueryHandler) frag.updateContentBasedOnQuery(query)
    }
}
