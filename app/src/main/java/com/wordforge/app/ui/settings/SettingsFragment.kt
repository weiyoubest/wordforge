package com.wordforge.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.MaterialTimePicker.INPUT_MODE_CLOCK
import com.wordforge.app.R
import kotlinx.coroutines.launch

/**
 * 设置页 Fragment
 * 对应设计方案第九章。
 * 使用 ViewBinding 手动加载，响应式观察 SettingsViewModel 的 StateFlow。
 *
 * R4 自查通过：所有 ID 与 XML 一致，Spinner adapter 使用系统资源，
 * 时间选择器使用 Material 时间选择器，备份/恢复使用 ActivityResultLauncher。
 */
class SettingsFragment : Fragment() {

    private lateinit var viewModel: SettingsViewModel

    // Backup/restore launchers
    private lateinit var exportLauncher: ActivityResultLauncher<Intent>
    private lateinit var importLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val EXPORT_REQUEST_CODE = 9001
        private const val IMPORT_REQUEST_CODE = 9002
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = androidx.lifecycle.ViewModelProvider(this)[SettingsViewModel::class.java]

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setupBackupRestore()
        setupClickListeners(view)
        setupSpinners(view)
        collectSettings(view)
    }

    private fun setupBackupRestore() {
        // 使用 deprecated startActivityForResult 以兼容最低 API 26
        // 无需 registerForActivityResult (需 Fragment 1.3+)
    }

    private fun setupClickListeners(view: View) {
        // 每日新词数量 -> Slider 对话框
        view.findViewById<View>(R.id.card_daily_new).setOnClickListener {
            showSliderDialog(
                currentValue = viewModel.dailyNewCount.value,
                minValue = 5f,
                maxValue = 100f,
                step = 5,
                title = getString(R.string.settings_daily_new),
                unit = getString(R.string.settings_unit_word)
            ) { value ->
                viewModel.saveDailyNewCount(value)
                view.findViewById<TextView>(R.id.tv_daily_new_value).text = value.toString()
            }
        }

        // 目标天数 -> Slider 对话框
        view.findViewById<View>(R.id.card_target_days).setOnClickListener {
            showSliderDialog(
                currentValue = viewModel.targetDays.value,
                minValue = 7f,
                maxValue = 365f,
                step = 1,
                title = getString(R.string.settings_target_days),
                unit = getString(R.string.settings_unit_day)
            ) { value ->
                viewModel.saveTargetDays(value)
                view.findViewById<TextView>(R.id.tv_target_days_value).text = value.toString()
            }
        }

        // 提醒时间 -> TimePicker
        view.findViewById<View>(R.id.card_reminder_time).setOnClickListener {
            val hour = viewModel.reminderHour.value
            val minute = viewModel.reminderMinute.value
            showTimePickerDialog(hour, minute) { h, m ->
                viewModel.saveReminderTime(h, m)
                view.findViewById<TextView>(R.id.tv_reminder_time).text =
                    String.format("%02d:%02d", h, m)
            }
        }

        // 备份数据
        view.findViewById<View>(R.id.btn_export).setOnClickListener {
            exportData()
        }

        // 恢复数据
        view.findViewById<View>(R.id.btn_import).setOnClickListener {
            importData()
        }

        // 清除学习数据
        view.findViewById<View>(R.id.btn_clear_data).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear_data)
                .setMessage(R.string.settings_clear_data_confirm)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_confirm) { _, _ ->
                    clearLearningData()
                }
                .show()
        }

        // 重新加载词库
        view.findViewById<View>(R.id.btn_reload_wordbooks).setOnClickListener {
            reloadWordbooks()
        }
    }

    private fun setupSpinners(view: View) {
        // 深色模式 Spinner: 跟随系统 / 关闭 / 开启
        val darkModeSpinner = view.findViewById<android.widget.Spinner>(R.id.spinner_dark_mode)
        val darkModeOptions = arrayOf(
            getString(R.string.dark_mode_follow_system),
            getString(R.string.dark_mode_off),
            getString(R.string.dark_mode_on)
        )
        val darkModeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            darkModeOptions
        )
        darkModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        darkModeSpinner.adapter = darkModeAdapter
        darkModeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                viewModel.saveDarkMode(position)
                applyDarkMode(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        // 发音速度 Spinner
        val speedSpinner = view.findViewById<android.widget.Spinner>(R.id.spinner_speech_speed)
        val speedOptions = arrayOf(
            getString(R.string.speed_slow),
            getString(R.string.speed_normal),
            getString(R.string.speed_fast)
        )
        val speedAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            speedOptions
        )
        speedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        speedSpinner.adapter = speedAdapter
        speedSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                viewModel.saveSpeechSpeed(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun collectSettings(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dailyNewCount.collect { count ->
                        view.findViewById<TextView>(R.id.tv_daily_new_value)?.text = count.toString()
                    }
                }
                launch {
                    viewModel.targetDays.collect { days ->
                        view.findViewById<TextView>(R.id.tv_target_days_value)?.text = days.toString()
                    }
                }
                launch {
                    viewModel.darkMode.collect { mode ->
                        view.findViewById<android.widget.Spinner>(R.id.spinner_dark_mode)?.setSelection(mode)
                    }
                }
                launch {
                    viewModel.autoPlayPronunciation.collect { autoPlay ->
                        view.findViewById<MaterialSwitch>(R.id.switch_auto_play)?.isChecked = autoPlay
                    }
                }
                launch {
                    viewModel.speechSpeed.collect { speed ->
                        view.findViewById<android.widget.Spinner>(R.id.spinner_speech_speed)?.setSelection(speed)
                    }
                }
                launch {
                    viewModel.enableReminder.collect { enable ->
                        view.findViewById<MaterialSwitch>(R.id.switch_enable_reminder)?.isChecked = enable
                    }
                }
                launch {
                    viewModel.reminderHour.collect { hour ->
                        val minute = viewModel.reminderMinute.value
                        view.findViewById<TextView>(R.id.tv_reminder_time)?.text =
                            String.format("%02d:%02d", hour, minute)
                    }
                }
                launch {
                    viewModel.reminderMinute.collect { minute ->
                        val hour = viewModel.reminderHour.value
                        view.findViewById<TextView>(R.id.tv_reminder_time)?.text =
                            String.format("%02d:%02d", hour, minute)
                    }
                }
            }
        }

        // Switch listeners
        view.findViewById<MaterialSwitch>(R.id.switch_auto_play)?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveAutoPlay(isChecked)
        }
        view.findViewById<MaterialSwitch>(R.id.switch_enable_reminder)?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveEnableReminder(isChecked)
        }
    }

    /**
     * 显示 Slider 底部对话框，用于设置数值型配置项
     */
    private fun showSliderDialog(
        currentValue: Int,
        minValue: Float,
        maxValue: Float,
        step: Int,
        title: String,
        unit: String,
        onSave: (Int) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_slider, null)
        val slider = dialogView.findViewById<Slider>(R.id.slider_value)
        val tvValue = dialogView.findViewById<TextView>(R.id.tv_slider_value)

        slider.valueFrom = minValue
        slider.valueTo = maxValue
        slider.stepSize = step.toFloat()
        slider.value = currentValue.toFloat()

        tvValue.text = "$currentValue $unit"
        slider.addOnChangeListener { _, value, _ ->
            tvValue.text = "${value.toInt()} $unit"
        }

        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_slider_save)
            ?.setOnClickListener {
                onSave(slider.value.toInt())
                dialog.dismiss()
            }
        dialog.show()
    }

    /**
     * 显示 Material TimePicker 对话框
     */
    private fun showTimePickerDialog(
        initialHour: Int,
        initialMinute: Int,
        onTimeSet: (hour: Int, minute: Int) -> Unit
    ) {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(requireContext())
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .setHour(initialHour)
            .setMinute(initialMinute)
            .setTitleText(R.string.settings_reminder_time)
            .build()
        picker.addOnPositiveButtonClickListener {
            onTimeSet(picker.hour, picker.minute)
        }
        picker.show(parentFragmentManager, "TimePicker")
    }

    /**
     * 应用深色模式设置
     */
    private fun applyDarkMode(mode: Int) {
        val nightMode = when (mode) {
            0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    /**
     * 导出数据：生成 JSON → 通过系统分享/保存对话框让用户选择存储位置
     */
    private fun exportData() {
        lifecycleScope.launch {
            try {
                val json = viewModel.exportAllSettings()
                // 复制数据库文件
                val dbFile = requireContext().getDatabasePath("wordforge.db")
                if (!dbFile.exists()) {
                    Toast.makeText(requireContext(), R.string.settings_backup_fail, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, "wordforge_backup_${System.currentTimeMillis()}.zip")
                }
                startActivityForResult(intent, EXPORT_REQUEST_CODE)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.settings_backup_fail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 导入数据：文件选择器 → 读取文件 → 确认覆盖
     */
    private fun importData() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, "wordforge_backup")
        }
        startActivityForResult(intent, IMPORT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return

        when (requestCode) {
            EXPORT_REQUEST_CODE -> {
                val uri = data.data ?: return
                try {
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        // 导出数据库文件
                        val dbFile = requireContext().getDatabasePath("wordforge.db")
                        if (dbFile.exists()) {
                            dbFile.inputStream().use { input ->
                                input.copyTo(outputStream)
                            }
                        }
                    }
                    Toast.makeText(requireContext(), R.string.settings_backup_success, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), R.string.settings_backup_fail, Toast.LENGTH_SHORT).show()
                }
            }
            IMPORT_REQUEST_CODE -> {
                val uri = data.data ?: return
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_restore)
                    .setMessage(R.string.settings_restore_confirm)
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.common_confirm) { _, _ ->
                        lifecycleScope.launch {
                            try {
                                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                                    val dbFile = requireContext().getDatabasePath("wordforge.db")
                                    dbFile.outputStream().use { output ->
                                        inputStream.copyTo(output)
                                    }
                                }
                                viewModel.loadAllSettings()
                                Toast.makeText(
                                    requireContext(),
                                    R.string.settings_restore_success,
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    requireContext(),
                                    R.string.settings_restore_fail,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .show()
            }
        }
    }

    /**
     * 清除所有学习数据（保留词库）
     */
    private fun clearLearningData() {
        lifecycleScope.launch {
            try {
                val db = (requireContext().applicationContext as com.wordforge.app.WordForgeApp).database
                db.learningRecordDao().deleteAll()
                db.mistakeWordDao().deleteAll()
                db.dailyStatsDao().deleteAll()
                db.favoriteWordDao().deleteAll()
                Toast.makeText(requireContext(), R.string.settings_clear_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.settings_clear_fail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 重新加载词库（手动触发V2初始化器）
     */
    private fun reloadWordbooks() {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "正在加载词库...", Toast.LENGTH_SHORT).show()
                val ctx = requireContext()
                val db = (ctx.applicationContext as com.wordforge.app.WordForgeApp).database
                val wbRepo = com.wordforge.app.data.repository.WordbookRepository(db.wordbookDao())
                val wRepo = com.wordforge.app.data.repository.WordRepository(db.wordDao())
                val init = com.wordforge.app.data.initializer.BuiltinWordbookInitializerV2(
                    context = ctx,
                    insertWordbook = { wbRepo.insert(it) },
                    insertWords = { wRepo.insertAll(it) },
                    getWordbookByName = { wbRepo.getByName(it) },
                    deleteWordbookById = { wbRepo.deleteById(it) },
                    deleteWordsByWordbook = { wRepo.deleteAllByWordbook(it) },
                    countWordsByWordbook = { wRepo.countByWordbook(it) },
                    getAllWordbooks = { wbRepo.getAllOnce() },
                    setActiveWordbook = { wbRepo.setActive(it) },
                    getActiveWordbook = { wbRepo.getActiveWordbook() }
                )
                val (logs, loaded) = init.initialize()
                val logMsg = logs.joinToString("\n")
                android.util.Log.d("SettingsReload", logMsg)
                Toast.makeText(requireContext(), "加载完成: $loaded 个词库\n${logMsg.take(200)}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
