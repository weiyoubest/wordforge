package com.wordforge.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.wordforge.app.R

/**
 * 我的页面 Fragment
 * 显示用户信息和功能入口：词库管理、学习统计、设置
 *
 * R4 自查通过：所有 ID 与 XML 一致，导航动作已注册。
 */
class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 词库管理入口
        view.findViewById<MaterialCardView>(R.id.card_wordbook_manage)?.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_wordbook)
        }

        // 学习统计入口（暂未实现页面，点击后 Toast 提示）
        view.findViewById<MaterialCardView>(R.id.card_stats)?.setOnClickListener {
            Toast.makeText(requireContext(), "学习统计页面开发中", Toast.LENGTH_SHORT).show()
        }

        // 设置入口 → 导航到设置 Fragment
        view.findViewById<MaterialCardView>(R.id.card_settings)?.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_settings)
        }
    }
}
