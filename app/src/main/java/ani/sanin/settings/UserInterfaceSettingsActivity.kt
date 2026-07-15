package ani.sanin.settings

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import ani.sanin.R
import ani.sanin.databinding.ActivityUserInterfaceSettingsBinding
import ani.sanin.initActivity
import ani.sanin.navBarHeight
import ani.sanin.restartApp
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.customAlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

class UserInterfaceSettingsActivity : AppCompatActivity() {
    lateinit var binding: ActivityUserInterfaceSettingsBinding
    private val ui = "ui_settings"

    private fun getFocusEffectLabel(effect: Int): String = when (effect) {
        0 -> "Icy Glow"
        1 -> "Glass Lift"
        2 -> "Crystal Edge"
        3 -> "Snap Bounce"
        4 -> "Serpent Pulse"
        5 -> "None"
        else -> "None"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityUserInterfaceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)
        binding.uiSettingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        binding.uiSettingsBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        FocusEffectUtil.applyFocusListener(
            binding.uiSettingsBack,
            binding.uiSettingsHomeLayout,
            binding.uiSettingsFocusEffect,
            binding.uiSettingsImmersive,
            binding.uiSettingsSmallView,
            binding.uiSettingsEmoji,
        )

        binding.uiSettingsHomeLayout.setOnClickListener {
            val currentVisibility = PrefManager.getVal<List<Boolean>>(PrefName.HomeLayout).toMutableList()
            var currentOrder = PrefManager.getVal<List<Int>>(PrefName.HomeLayoutOrder).toMutableList()
            val views = resources.getStringArray(R.array.home_layouts)
            val fixedIndex = 7

            if (currentVisibility.size < views.size) {
                repeat(views.size - currentVisibility.size) { currentVisibility.add(true) }
            } else if (currentVisibility.size > views.size) {
                currentVisibility.subList(views.size, currentVisibility.size).clear()
            }

            val reorderable = views.indices.filter { it != fixedIndex }
            if (currentOrder.isEmpty()) {
                currentOrder = reorderable.toMutableList()
            } else {
                val sanitizedOrder = currentOrder.filter { it in reorderable }.distinct().toMutableList()
                val missing = reorderable.filterNot { it in sanitizedOrder }
                sanitizedOrder.addAll(missing)
                currentOrder = sanitizedOrder
            }

            val displayList = mutableListOf(fixedIndex)
            displayList.addAll(currentOrder.filter { it != fixedIndex })

            val recyclerView = RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@UserInterfaceSettingsActivity)
                setPadding(0, 32, 0, 0)
                clipToPadding = false
            }
            val adapter = HomeLayoutAdapter(displayList, views, currentVisibility)
            recyclerView.adapter = adapter

            val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val fromPos = viewHolder.bindingAdapterPosition
                    val toPos = target.bindingAdapterPosition

                    if (fromPos == 0 || toPos == 0) return false

                    val item = displayList.removeAt(fromPos)
                    displayList.add(toPos, item)
                    adapter.notifyItemMoved(fromPos, toPos)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.elevation = 0f
                }

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder?.itemView?.elevation = 8f
                    }
                }
            })
            itemTouchHelper.attachToRecyclerView(recyclerView)

            customAlertDialog().apply {
                setTitle(getString(R.string.home_layout_show))
                setCustomView(recyclerView)
                setPosButton(R.string.ok) {
                    PrefManager.setVal(PrefName.HomeLayout, currentVisibility)
                    PrefManager.setVal(PrefName.HomeLayoutOrder, displayList.drop(1))
                    restartApp()
                }
                setNegButton(R.string.cancel, null)
                show()
            }
        }

        binding.uiSettingsSmallView.isChecked = PrefManager.getVal(PrefName.SmallView)
        binding.uiSettingsSmallView.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.SmallView, isChecked)
            restartApp()
        }

        binding.uiSettingsImmersive.isChecked = PrefManager.getVal(PrefName.ImmersiveMode)
        binding.uiSettingsImmersive.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ImmersiveMode, isChecked)
            restartApp()
        }

        binding.uiSettingsFocusEffect.setOnClickListener {
            customAlertDialog().apply {
                setTitle("Focus Effect")
                val labels = arrayOf("Icy Glow", "Glass Lift", "Crystal Edge", "Snap Bounce", "Serpent Pulse", "None")
                singleChoiceItems(labels, PrefManager.getVal<Int>(PrefName.FocusEffect)) { index ->
                    PrefManager.setVal(PrefName.FocusEffect, index)
                }
                show()
            }
        }

        binding.uiSettingsEmoji.isChecked = PrefManager.getVal(PrefName.Emoji)
        binding.uiSettingsEmoji.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.Emoji, isChecked)
        }
    }

    inner class HomeLayoutAdapter(
        private val displayList: MutableList<Int>,
        private val views: Array<String>,
        private val currentVisibility: MutableList<Boolean>
    ) : RecyclerView.Adapter<HomeLayoutAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dragHandle: ImageView = view.findViewById(R.id.itemHomeLayoutDragHandle)
            val title: TextView = view.findViewById(R.id.itemHomeLayoutTitle)
            val switch: MaterialSwitch = view.findViewById(R.id.itemHomeLayoutSwitch)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_layout, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val originalIndex = displayList[position]
            holder.title.text = views[originalIndex]
            
            holder.switch.setOnCheckedChangeListener(null)
            holder.switch.isChecked = currentVisibility[originalIndex]
            holder.switch.setOnCheckedChangeListener { _, isChecked ->
                currentVisibility[originalIndex] = isChecked
            }

            if (position == 0) {
                holder.dragHandle.visibility = View.INVISIBLE
            } else {
                holder.dragHandle.visibility = View.VISIBLE
            }
        }

        override fun getItemCount(): Int = displayList.size
    }
}
