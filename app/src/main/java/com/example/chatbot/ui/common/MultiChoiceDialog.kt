package com.example.chatbot.ui.common

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.example.chatbot.R
import com.example.chatbot.databinding.DialogMultiChoiceBinding

class MultiChoiceDialog(
    private val title: String,
    private val items: List<String>,
    private val checkedItems: BooleanArray,
    private val onConfirm: (Set<Int>) -> Unit
) : DialogFragment() {

    private var _binding: DialogMultiChoiceBinding? = null
    private val binding get() = _binding!!
    
    private var selectedItems = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Chatbot_Dialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val width = (resources.displayMetrics.widthPixels * 0.85f).toInt()
            val height = (resources.displayMetrics.heightPixels * 0.6f).toInt()
            window.setLayout(width, height)
            window.setGravity(Gravity.CENTER)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMultiChoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = title
        
        // 初始化已选中的项目
        checkedItems.forEachIndexed { index, checked ->
            if (checked) {
                selectedItems.add(index)
            }
        }
        
        // 设置适配器
        binding.listView.adapter = object : BaseAdapter() {
            override fun getCount(): Int = items.size
            
            override fun getItem(position: Int): Any = items[position]
            
            override fun getItemId(position: Int): Long = position.toLong()
            
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val container = if (convertView != null && convertView is ViewGroup) {
                    convertView
                } else {
                    LinearLayout(context).apply {
                        layoutParams = android.widget.AbsListView.LayoutParams(
                            android.widget.AbsListView.LayoutParams.MATCH_PARENT,
                            resources.getDimensionPixelSize(R.dimen.kd_space_1200)
                        )
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(
                            resources.getDimensionPixelSize(R.dimen.kd_space_400),
                            0,
                            resources.getDimensionPixelSize(R.dimen.kd_space_400),
                            0
                        )
                    }
                }
                
                // 清除旧视图
                if (container.childCount > 0) {
                    container.removeAllViews()
                }
                
                val checkBox = CheckBox(context).apply {
                    id = 1
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    text = items[position]
                    isChecked = selectedItems.contains(position)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedItems.add(position)
                        } else {
                            selectedItems.remove(position)
                        }
                    }
                }
                container.addView(checkBox)
                
                return container
            }
        }
        
        binding.btnPositive.setOnClickListener {
            onConfirm(selectedItems)
            dismiss()
        }
        
        binding.btnNegative.setOnClickListener {
            dismiss()
        }
        
        binding.btnNeutral.setOnClickListener {
            onConfirm(emptySet())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
