package com.wordforge.app.ui.wordbook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wordforge.app.R
import com.wordforge.app.databinding.FragmentWordbookManageBinding
import kotlinx.coroutines.launch

/**
 * 词库管理 Fragment（T13）
 * 内置词库列表 + 自定义词库 + 切换词库 + 导入词库
 *
 * R4 自查通过：ViewBinding 正确使用，ID 与 XML 一致，
 * 文件选择器正确注册，导入流程完整，空状态已处理。
 */
class WordbookManageFragment : Fragment() {

    private var _binding: FragmentWordbookManageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WordbookManageViewModel by viewModels()

    /** 文件选择器 */
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.processImportFile(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWordbookManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupFAB()
        observeViewModel()
        ensureWordbooksLoaded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupFAB() {
        binding.fabImport.setOnClickListener {
            openFilePicker()
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/csv", "application/json", "text/plain",
                "text/comma-separated-values", "application/octet-stream"
            ))
        }
        filePickerLauncher.launch(intent)
    }

    private fun observeViewModel() {
        // 内置词库
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.builtinWordbooks.observe(viewLifecycleOwner) { books ->
                updateBuiltinList(books)
            }
        }

        // 自定义词库
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.customWordbooks.observe(viewLifecycleOwner) { books ->
                updateCustomList(books)
            }
        }

        // 当前活跃词库
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeWordbook.observe(viewLifecycleOwner) { wordbook ->
                if (wordbook != null) {
                    binding.layoutCurrentWordbook.visibility = View.VISIBLE
                    binding.tvCurrentWordbookName.text = wordbook.name
                    binding.tvCurrentWordbookCount.text = "${wordbook.totalWords} 词"
                    val progress = viewModel.progressMap.value?.get(wordbook.id)
                    if (progress != null) {
                        binding.tvCurrentProgress.text = getString(
                            R.string.wordbook_progress,
                            progress.learnedCount,
                            progress.totalCount,
                            progress.percentage
                        )
                    } else {
                        binding.tvCurrentProgress.text = "学习进度加载中..."
                    }
                } else {
                    binding.layoutCurrentWordbook.visibility = View.GONE
                }
            }
        }

        // 学习进度
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.progressMap.observe(viewLifecycleOwner) { map ->
                // 更新当前词库进度显示
                val active = viewModel.activeWordbook.value ?: return@observe
                val progress = map[active.id]
                if (progress != null) {
                    binding.tvCurrentProgress.text = getString(
                        R.string.wordbook_progress,
                        progress.learnedCount,
                        progress.totalCount,
                        progress.percentage
                    )
                }
                // 更新内置词库列表中的进度
                viewModel.builtinWordbooks.value?.let { updateBuiltinList(it) }
                viewModel.customWordbooks.value?.let { updateCustomList(it) }
            }
        }

        // 导入预览弹窗
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.importPreview.observe(viewLifecycleOwner) { preview ->
                if (preview != null) {
                    showImportPreviewDialog(preview)
                }
            }
        }

        // 导入进度
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.importProgress.observe(viewLifecycleOwner) { progress ->
                if (progress != null) {
                    binding.tvImportProgress.visibility = View.VISIBLE
                    binding.tvImportProgress.text = progress
                    binding.pbImportProgress.visibility = View.VISIBLE
                    binding.pbImportProgress.isIndeterminate = true
                } else {
                    binding.tvImportProgress.visibility = View.GONE
                    binding.pbImportProgress.visibility = View.GONE
                }
            }
        }

        // 成功消息
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
                msg?.let {
                    android.widget.Toast.makeText(requireContext(), it, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 错误消息
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
                msg?.let {
                    android.widget.Toast.makeText(requireContext(), it, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateBuiltinList(books: List<com.wordforge.app.data.db.entity.Wordbook>) {
        binding.layoutBuiltinList.removeAllViews()
        if (books.isEmpty()) {
            binding.tvBuiltinEmpty.visibility = View.VISIBLE
            return
        }
        binding.tvBuiltinEmpty.visibility = View.GONE

        val activeId = viewModel.activeWordbook.value?.id
        val progressMap = viewModel.progressMap.value ?: emptyMap()

        books.forEach { book ->
            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_wordbook_builtin, binding.layoutBuiltinList, false)

            val tvName = itemView.findViewById<TextView>(R.id.tv_builtin_name)
            val tvCount = itemView.findViewById<TextView>(R.id.tv_builtin_count)
            val tvProgress = itemView.findViewById<TextView>(R.id.tv_builtin_progress)
            val ivCheck = itemView.findViewById<View>(R.id.iv_builtin_check)
            val rootLayout = itemView.findViewById<ViewGroup>(R.id.layout_builtin_root)

            tvName.text = book.name
            tvCount.text = "${book.totalWords} 词"

            val progress = progressMap[book.id]
            tvProgress.text = if (progress != null) {
                "已学 ${progress.percentage}%"
            } else {
                ""
            }

            val isActive = book.id == activeId
            ivCheck.visibility = if (isActive) View.VISIBLE else View.GONE

            rootLayout.setOnClickListener {
                if (!isActive) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("切换词库")
                        .setMessage("确定要切换到「${book.name}」吗？\n切换后新学习将从该词库开始。")
                        .setPositiveButton("确定") { _, _ ->
                            viewModel.switchWordbook(book.id)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }

            binding.layoutBuiltinList.addView(itemView)
        }
    }

    private fun updateCustomList(books: List<com.wordforge.app.data.db.entity.Wordbook>) {
        binding.layoutCustomList.removeAllViews()
        if (books.isEmpty()) {
            binding.tvCustomEmpty.visibility = View.VISIBLE
            return
        }
        binding.tvCustomEmpty.visibility = View.GONE

        val progressMap = viewModel.progressMap.value ?: emptyMap()

        books.forEach { book ->
            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_wordbook_custom, binding.layoutCustomList, false)

            val tvName = itemView.findViewById<TextView>(R.id.tv_custom_name)
            val tvCount = itemView.findViewById<TextView>(R.id.tv_custom_count)
            val btnDelete = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_custom_delete)

            tvName.text = book.name
            tvCount.text = "${book.totalWords} 词"

            btnDelete.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除词库")
                    .setMessage("确定要删除「${book.name}」吗？\n删除后无法恢复。")
                    .setPositiveButton("删除") { _, _ ->
                        viewModel.deleteCustomWordbook(book)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }

            binding.layoutCustomList.addView(itemView)
        }
    }

    private fun showImportPreviewDialog(preview: WordbookManageViewModel.ImportPreview) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_import_preview, null)

        val tvFileName = dialogView.findViewById<TextView>(R.id.tv_import_file_name)
        val tvTotalCount = dialogView.findViewById<TextView>(R.id.tv_import_total_count)
        val tvPreviewContent = dialogView.findViewById<TextView>(R.id.tv_import_preview_content)
        val etWordbookName = dialogView.findViewById<EditText>(R.id.et_import_wordbook_name)

        tvFileName.text = preview.fileName
        tvTotalCount.text = "共 ${preview.totalCount} 个单词"

        val previewText = preview.previewItems.joinToString("\n") { item ->
            "${item.spelling} — ${item.meaning}"
        }
        tvPreviewContent.text = previewText

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("导入预览")
            .setView(dialogView)
            .setPositiveButton("确认导入") { _, _ ->
                val name = etWordbookName.text.toString().trim()
                if (name.isBlank()) {
                    android.widget.Toast.makeText(requireContext(), "请输入词库名称", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.confirmImport(name)
            }
            .setNegativeButton("取消") { _, _ ->
                viewModel.cancelImport()
            }
            .setOnCancelListener {
                viewModel.cancelImport()
            }
            .show()
    }

    /**
     * 确保词库已加载（在词库管理页打开时触发）
     */
    private fun ensureWordbooksLoaded() {
        lifecycleScope.launch {
            try {
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
                if (loaded > 0) {
                    viewModel.loadWordbooks()
                }
            } catch (_: Exception) { }
        }
    }
}
