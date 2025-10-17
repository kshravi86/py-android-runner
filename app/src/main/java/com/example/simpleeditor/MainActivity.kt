package com.example.simpleeditor

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import android.view.ViewGroup
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.color.DynamicColors
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.simpleeditor.databinding.ActivityMainBinding
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.noties.markwon.Markwon
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import java.nio.charset.StandardCharsets
import java.io.File
import androidx.core.content.res.ResourcesCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.SystemClock

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var outputVisible: Boolean = true
    private val syntaxHandler = Handler(Looper.getMainLooper())
    private var syntaxRunnable: Runnable? = null
    private var execCount: Int = 0
    private var mdCount: Int = 0
    private var markwon: Markwon? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Show splash as early as possible
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Apply dynamic color (Android 12+) for a modern, personalized palette
        DynamicColors.applyToActivityIfAvailable(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        // Ensure toolbar menu icons are visible (white on gradient)
        try {
            val white = ContextCompat.getColor(this, android.R.color.white)
            binding.topAppBar.post {
                val m = binding.topAppBar.menu
                for (i in 0 until m.size()) {
                    m.getItem(i).icon?.setTint(white)
                }
                binding.topAppBar.overflowIcon?.setTint(white)
                binding.topAppBar.navigationIcon?.setTint(white)
            }
        } catch (_: Throwable) { }
        ensurePython()
        setupCodeEditor()
        applySavedFontSize()
        setupDefaultCode(savedInstanceState)
        setupToolbarActions()
        setupQuickChips()
        setupImeRunFab()
        // Run action is on the top app bar; bottom FAB removed

        // Debounced syntax check on content changes
        subscribeSyntaxCheck()
    }

    override fun onPause() {
        super.onPause()
        savePersistedState()
    }

    private fun setupQuickChips() {
        binding.chipRun.setOnClickListener { runCurrentCell() }
        binding.chipToggle.setOnClickListener { toggleOutputVisibility() }
        binding.chipClear.setOnClickListener {
            setEditorText("")
            resetOutput()
        }
        binding.chipSave.setOnClickListener { saveCodeToFile() }
        binding.chipShare.setOnClickListener { shareCode() }
        binding.chipRunAll.setOnClickListener { runAllCells() }
        binding.chipAddCell.setOnClickListener { insertCellSeparator() }
        binding.chipClearOutput.setOnClickListener { clearOutputOnly() }
        binding.chipResetSession.setOnClickListener { resetPythonSession() }
        binding.chipRunAbove.setOnClickListener { runAboveCells() }
        binding.chipRunBelow.setOnClickListener { runBelowCells() }
        binding.chipPreviewMd.setOnClickListener { previewCurrentMarkdown() }
    }

    private fun setupImeRunFab() {
        // Show a bottom Run FAB when the keyboard is visible
        binding.fabImeRun.setOnClickListener { runCurrentCell() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            binding.fabImeRun.visibility = if (imeVisible) View.VISIBLE else View.GONE
            insets
        }
    }

    private fun insertCellSeparator() {
        val sep = "# %%\n"
        val text = binding.editorView.text
        val cursor = text.cursor
        val line = cursor.leftLine
        val start = text.getCharIndex(line, 0)
        text.insert(start, 0, sep)
    }

    private fun resetPythonSession() {
        try {
            val python = Python.getInstance()
            val module = python.getModule("runner")
            module.callAttr("reset_session")
            execCount = 0
        } catch (_: Throwable) { }
    }

    private fun runCurrentCell() {
        val cell = getCurrentCellCode() ?: run {
            Toast.makeText(this, "No cell found", Toast.LENGTH_SHORT).show()
            return
        }
        val start = SystemClock.elapsedRealtime()
        val out = runPythonCell(cell)
        val dur = SystemClock.elapsedRealtime() - start
        execCount += 1
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        appendPythonOutput(execCount, timeStr, dur, out)
    }

    private fun runAllCells() {
        val cells = parseCells()
        if (cells.isEmpty()) return
        for (cell in cells) executeCell(cell)
    }

    private fun appendTerminalOutput(header: String, body: String) {
        val existing = binding.outputView.text
        val builder = android.text.SpannableStringBuilder()
        if (!existing.isNullOrBlank()) {
            builder.append(existing)
            builder.append("\n\n")
        }
        builder.append(header)
        builder.append('\n')
        val start = builder.length
        val text = body.trimEnd()
        builder.append(text)
        colorizeErrors(builder, start, builder.length)
        binding.outputView.text = builder
        binding.outputCard.visibility = View.VISIBLE
        outputVisible = true
        restoreEditorAboveOutput()
        binding.outputScroll.post { binding.outputScroll.fullScroll(View.FOCUS_DOWN) }
        savePersistedState()
    }

    private fun appendTerminalOutput(header: String, spannedBody: android.text.Spanned) {
        val existing = binding.outputView.text
        val builder = android.text.SpannableStringBuilder()
        if (!existing.isNullOrBlank()) {
            builder.append(existing)
            builder.append("\n\n")
        }
        builder.append(header)
        builder.append('\n')
        builder.append(spannedBody)
        binding.outputView.text = builder
        binding.outputCard.visibility = View.VISIBLE
        outputVisible = true
        restoreEditorAboveOutput()
        binding.outputScroll.post { binding.outputScroll.fullScroll(View.FOCUS_DOWN) }
        savePersistedState()
    }

    private fun colorizeErrors(builder: android.text.SpannableStringBuilder, start: Int, end: Int) {
        val red = ContextCompat.getColor(this, R.color.brand_error)
        val lines = builder.subSequence(start, end).toString().split('\n')
        var offset = start
        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("Traceback (most recent call last):") ||
                Regex("^[A-Za-z_]+Error:.*").matches(trimmed) ||
                trimmed.startsWith("Exception:")) {
                builder.setSpan(android.text.style.ForegroundColorSpan(red), offset, offset + line.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            offset += line.length + 1
        }
    }

    private data class Cell(val startLine: Int, val endLine: Int, val type: String)

    private fun appendPythonOutput(n: Int, timeStr: String, durMs: Long, body: String) {
        val existing = binding.outputView.text
        val builder = android.text.SpannableStringBuilder()
        if (!existing.isNullOrBlank()) {
            builder.append(existing)
            builder.append("\n\n")
        }
        // In header
        builder.append("In [").append(n.toString()).append("]")
        builder.append('\n')
        // Time line (dim)
        val dimColor = ContextCompat.getColor(this, R.color.terminal_dim)
        val timeLineStart = builder.length
        builder.append(timeStr).append(" • ").append(durMs.toString()).append(" ms")
        builder.setSpan(android.text.style.ForegroundColorSpan(dimColor), timeLineStart, builder.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.append('\n')
        // Out label
        builder.append("Out [").append(n.toString()).append("]: ")
        builder.append('\n')
        // Body
        val bodyStart = builder.length
        builder.append(body.trimEnd())
        colorizeErrors(builder, bodyStart, builder.length)
        binding.outputView.text = builder
        binding.outputCard.visibility = View.VISIBLE
        outputVisible = true
        restoreEditorAboveOutput()
        binding.outputScroll.post { binding.outputScroll.fullScroll(View.FOCUS_DOWN) }
        savePersistedState()
    }

    private fun parseCells(): List<Cell> {
        val text = binding.editorView.text
        val lineCount = text.lineCount
        fun isMarker(i: Int): Boolean = text.getLineString(i).trimStart().startsWith("# %%")
        fun isMdMarker(i: Int): Boolean {
            val s = text.getLineString(i).lowercase()
            return s.trimStart().startsWith("# %%") && (s.contains("markdown") || s.contains(" md"))
        }
        val cells = mutableListOf<Cell>()
        var currentStart = 0
        var currentType = "py"
        var i = 0
        while (i < lineCount) {
            if (isMarker(i)) {
                if (i > currentStart) cells.add(Cell(currentStart, i - 1, currentType))
                currentStart = i + 1
                currentType = if (isMdMarker(i)) "md" else "py"
            }
            i++
        }
        if (currentStart <= lineCount - 1) cells.add(Cell(currentStart, lineCount - 1, currentType))
        return cells
    }

    private fun getCurrentCellIndex(cells: List<Cell>): Int {
        val text = binding.editorView.text
        val line = text.cursor.leftLine
        return cells.indexOfFirst { line in it.startLine..it.endLine }
    }

    private fun runAboveCells() {
        val cells = parseCells()
        if (cells.isEmpty()) return
        val idx = getCurrentCellIndex(cells)
        if (idx <= 0) return
        for (i in 0 until idx) executeCell(cells[i])
    }

    private fun runBelowCells() {
        val cells = parseCells()
        if (cells.isEmpty()) return
        val idx = getCurrentCellIndex(cells).coerceAtLeast(0)
        for (i in idx until cells.size) executeCell(cells[i])
    }

    private fun previewCurrentMarkdown() {
        val cells = parseCells()
        if (cells.isEmpty()) return
        val idx = getCurrentCellIndex(cells)
        if (idx == -1) return
        val cell = cells[idx]
        val code = getCellText(cell)
        val md = code.trim('\n')
        val mk = markwon ?: Markwon.create(this).also { markwon = it }
        val spanned = mk.toMarkdown(md)
        appendTerminalOutput("Markdown", spanned)
    }

    private fun getCellText(cell: Cell): String {
        val text = binding.editorView.text
        val startIdx = text.getCharIndex(cell.startLine, 0)
        val endIdx = text.getCharIndex(cell.endLine, text.getColumnCount(cell.endLine))
        return text.substring(startIdx, endIdx)
    }

    private fun executeCell(cell: Cell) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        when (cell.type) {
            "md" -> {
                val mk = markwon ?: Markwon.create(this).also { markwon = it }
                val spanned = mk.toMarkdown(getCellText(cell))
                mdCount += 1
                // Build a header + dim time line before markdown content
                val header = "Markdown [${mdCount}]"
                val dimColor = ContextCompat.getColor(this, R.color.terminal_dim)
                val existing = binding.outputView.text
                val builder = android.text.SpannableStringBuilder()
                if (!existing.isNullOrBlank()) {
                    builder.append(existing)
                    builder.append("\n\n")
                }
                builder.append(header).append('\n')
                val start = builder.length
                builder.append(timeStr)
                builder.setSpan(android.text.style.ForegroundColorSpan(dimColor), start, builder.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append('\n')
                builder.append(spanned)
                binding.outputView.text = builder
                binding.outputCard.visibility = View.VISIBLE
                outputVisible = true
                restoreEditorAboveOutput()
                binding.outputScroll.post { binding.outputScroll.fullScroll(View.FOCUS_DOWN) }
            }
            else -> {
                val t0 = SystemClock.elapsedRealtime()
                val out = runPythonCell(getCellText(cell))
                val dur = SystemClock.elapsedRealtime() - t0
                execCount += 1
                appendPythonOutput(execCount, timeStr, dur, out)
            }
        }
    }

    private fun clearOutputOnly() {
        animateConstraints()
        binding.outputView.text = ""
        binding.outputCard.visibility = View.GONE
        outputVisible = false
        expandEditorToBottom()
        savePersistedState()
    }

    private fun getCurrentCellCode(): String? {
        val text = binding.editorView.text
        val curLine = text.cursor.leftLine
        val lineCount = text.lineCount
        fun isCellMarker(line: Int): Boolean {
            val s = text.getLineString(line)
            return s.trimStart().startsWith("# %%")
        }
        var startLine = 0
        var endLine = lineCount - 1
        // find start
        run {
            var i = curLine
            while (i >= 0) {
                if (isCellMarker(i)) { startLine = i + 1; break }
                i--
            }
        }
        // find end
        run {
            var i = curLine + 1
            while (i < lineCount) {
                if (isCellMarker(i)) { endLine = i - 1; break }
                i++
            }
        }
        if (startLine > endLine) return ""
        val startIdx = text.getCharIndex(startLine, 0)
        val endIdx = text.getCharIndex(endLine, text.getColumnCount(endLine))
        val code = text.substring(startIdx, endIdx)
        return code
    }

    private fun runPythonCell(code: String): String {
        if (code.isBlank()) return ""
        return try {
            val python = Python.getInstance()
            val module = python.getModule("runner")
            module.callAttr("run_cell", code).toString()
        } catch (e: PyException) {
            e.message ?: e.toString()
        }
    }

    private fun ensurePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }

    private fun setupDefaultCode(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            loadPersistedState()
        }
    }

    private fun setupToolbarActions() {
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_run -> {
                    runAllCells()
                    true
                }
                R.id.action_clear -> {
                    setEditorText("")
                    resetOutput()
                    true
                }
                R.id.action_toggle_output -> {
                    toggleOutputVisibility()
                    true
                }
                R.id.action_font_increase -> {
                    adjustFontSize(+2f)
                    true
                }
                R.id.action_font_decrease -> {
                    adjustFontSize(-2f)
                    true
                }
                R.id.action_save -> {
                    saveCodeToFile()
                    true
                }
                R.id.action_share -> {
                    shareCode()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleOutputVisibility() {
        animateConstraints()
        if (binding.outputView.text.isNullOrBlank()) {
            Toast.makeText(this, "No output to show", Toast.LENGTH_SHORT).show()
            return
        }
        outputVisible = !outputVisible
        if (outputVisible) {
            binding.outputCard.visibility = View.VISIBLE
            restoreEditorAboveOutput()
        } else {
            binding.outputCard.visibility = View.GONE
            expandEditorToBottom()
        }
    }

    // Inline buttons removed to maximize editor space. Use toolbar actions instead.

    private fun resetOutput() {
        animateConstraints()
        binding.outputView.text = ""
        binding.outputCard.visibility = View.GONE
        outputVisible = false
        expandEditorToBottom()
    }

    private fun displayResult(result: String) {
        animateConstraints()
        val text = result.trim()
        if (text.isEmpty()) {
            binding.outputView.text = ""
            binding.outputCard.visibility = View.GONE
            outputVisible = false
            expandEditorToBottom()
        } else {
            binding.outputView.text = text
            binding.outputCard.visibility = View.VISIBLE
            outputVisible = true
            restoreEditorAboveOutput()
            binding.outputScroll.post { binding.outputScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun runPythonCode(code: String): String {
        if (code.isBlank()) {
            return ""
        }
        return try {
            val python = Python.getInstance()
            val module = python.getModule("runner")
            module.callAttr("run_user_code", code).toString()
        } catch (error: PyException) {
            error.message ?: error.toString()
        }
    }

    private fun setupCodeEditor() {
        val enabledTm = tryEnableTextMate()
        binding.editorView.setLineNumberEnabled(true)
        binding.editorView.setTextSize(18f)
        // Apply a better code font with fallback
        try {
            val tf = try { ResourcesCompat.getFont(this, R.font.firacode_regular) } catch (_: Throwable) { null }
                ?: ResourcesCompat.getFont(this, R.font.jetbrains_mono)
            if (tf != null) {
                binding.editorView.setTypefaceText(tf)
                binding.editorView.setLigatureEnabled(true)
            }
        } catch (_: Throwable) { }
        // Replace default completion with Python-aware keywords/snippets
        try {
            val custom = com.example.simpleeditor.completion.PythonAutoCompletion(binding.editorView)
            binding.editorView.replaceComponent(
                io.github.rosemoe.sora.widget.component.EditorAutoCompletion::class.java,
                custom
            )
            custom.setEnabled(true)
            custom.setCompletionWndPositionMode(io.github.rosemoe.sora.widget.component.EditorAutoCompletion.WINDOW_POS_MODE_AUTO)
        } catch (_: Throwable) {
            // Ignore if API changes
        }
    }

    private fun applySavedFontSize() {
        val sp = getSharedPreferences("prefs", MODE_PRIVATE)
        val size = sp.getFloat("editor_text_size_sp", 18f)
        binding.editorView.setTextSize(size)
    }

    private fun adjustFontSize(delta: Float) {
        val spref = getSharedPreferences("prefs", MODE_PRIVATE)
        val current = spref.getFloat("editor_text_size_sp", 18f)
        val newSize = (current + delta).coerceIn(12f, 28f)
        binding.editorView.setTextSize(newSize)
        spref.edit().putFloat("editor_text_size_sp", newSize).apply()
        Toast.makeText(this, "Font: ${"%.0f".format(newSize)}sp", Toast.LENGTH_SHORT).show()
    }

    private fun expandEditorToBottom() {
        val root = binding.root as ConstraintLayout
        val set = ConstraintSet()
        set.clone(root)
        set.clear(binding.editorCard.id, ConstraintSet.BOTTOM)
        set.connect(binding.editorCard.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        set.applyTo(root)
    }

    private fun restoreEditorAboveOutput() {
        val root = binding.root as ConstraintLayout
        val set = ConstraintSet()
        set.clone(root)
        set.connect(binding.editorCard.id, ConstraintSet.BOTTOM, binding.outputCard.id, ConstraintSet.TOP)
        set.connect(binding.outputCard.id, ConstraintSet.TOP, binding.editorCard.id, ConstraintSet.BOTTOM)
        set.connect(binding.outputCard.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        set.applyTo(root)
    }

    private fun animateConstraints(durationMs: Long = 220) {
        val vg = binding.root as ViewGroup
        TransitionManager.beginDelayedTransition(vg, AutoTransition().apply { duration = durationMs })
    }

    private fun subscribeSyntaxCheck() {
        // Reuse editor's event flow by posting after user edits
        val content = binding.editorView.text
        content.addContentListener(object : io.github.rosemoe.sora.text.ContentListener {
            override fun beforeReplace(content: io.github.rosemoe.sora.text.Content) {}
            override fun afterInsert(content: io.github.rosemoe.sora.text.Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, text: CharSequence) {
                scheduleSyntaxCheck()
            }
            override fun afterDelete(content: io.github.rosemoe.sora.text.Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, deleted: CharSequence) {
                scheduleSyntaxCheck()
            }
            override fun beforeModification(content: io.github.rosemoe.sora.text.Content) {}
        })
        scheduleSyntaxCheck()
    }

    private fun scheduleSyntaxCheck(delayMs: Long = 400) {
        syntaxRunnable?.let { syntaxHandler.removeCallbacks(it) }
        val r = Runnable { performSyntaxCheck() }
        syntaxRunnable = r
        syntaxHandler.postDelayed(r, delayMs)
    }

    private fun performSyntaxCheck() {
        val code = getEditorText()
        if (code.isBlank()) {
            binding.editorView.setDiagnostics(DiagnosticsContainer())
            return
        }
        try {
            val python = Python.getInstance()
            val module = python.getModule("runner")
            val result = module.callAttr("check_syntax", code).toString()
            val container = DiagnosticsContainer()
            if (result.startsWith("ERR:")) {
                val parts = result.split(":", limit = 4)
                if (parts.size >= 4) {
                    val line = parts[1].toIntOrNull() ?: 1
                    val col = parts[2].toIntOrNull() ?: 1
                    val msg = parts[3]
                    val startIndex = binding.editorView.text.getCharIndex((line - 1).coerceAtLeast(0), (col - 1).coerceAtLeast(0))
                    val lineLen = binding.editorView.text.getColumnCount((line - 1).coerceAtLeast(0))
                    val endIndex = binding.editorView.text.getCharIndex((line - 1).coerceAtLeast(0), lineLen)
                    val detail = DiagnosticDetail(msg, msg, emptyList(), null)
                    val region = DiagnosticRegion(startIndex, endIndex, DiagnosticRegion.SEVERITY_ERROR, System.nanoTime(), detail)
                    container.addDiagnostic(region)
                }
            }
            binding.editorView.setDiagnostics(container)
        } catch (_: Throwable) {
            // Ignore checker failure to not interrupt typing
        }
    }

    private fun tryEnableTextMate(): Boolean {
        return try {
            val files = assets.list("textmate")?.toSet() ?: emptySet()
            val hasGrammar = files.contains("Python.tmLanguage") || files.contains("python.tmLanguage.json")
            val hasTheme = files.contains("dark_plus.json") || files.contains("OneDark.json")
            if (!hasGrammar || !hasTheme) return false

            val fpr = FileProviderRegistry.getInstance()
            fpr.addFileProvider(AssetsFileResolver(assets))

            val themePath = if (files.contains("dark_plus.json")) "textmate/dark_plus.json" else "textmate/OneDark.json"
            val themeStream = fpr.tryGetInputStream(themePath) ?: return false
            val themeSource = IThemeSource.fromInputStream(themeStream, themePath, StandardCharsets.UTF_8)
            val themeRegistry = ThemeRegistry.getInstance()
            themeRegistry.loadTheme(themeSource)
            binding.editorView.colorScheme = TextMateColorScheme.create(themeRegistry)

            val grammarFile = if (files.contains("Python.tmLanguage")) "textmate/Python.tmLanguage" else "textmate/python.tmLanguage.json"
            val grammarStream = fpr.tryGetInputStream(grammarFile) ?: return false
            val grammarSource = IGrammarSource.fromInputStream(grammarStream, grammarFile, StandardCharsets.UTF_8)
            val language = TextMateLanguage.create(grammarSource, themeSource)
            binding.editorView.setEditorLanguage(language)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun getEditorText(): String = binding.editorView.text.toString()

    private fun setEditorText(text: String) {
        binding.editorView.setText(text)
    }

    private fun saveCodeToFile() {
        try {
            openFileOutput(SAVED_CODE_FILE, MODE_PRIVATE).use { it.write(getEditorText().toByteArray()) }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readSavedCode(): String? {
        val file = File(filesDir, SAVED_CODE_FILE)
        if (!file.exists()) return null
        return try {
            file.readText()
        } catch (_: Exception) {
            null
        }
    }

    private fun shareCode() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, getEditorText())
            .putExtra(Intent.EXTRA_SUBJECT, "Python code from Python Runner")
        startActivity(Intent.createChooser(intent, "Share code via"))
    }

    private fun savePersistedState() {
        try {
            openFileOutput(NOTEBOOK_FILE, MODE_PRIVATE).use { it.write(getEditorText().toByteArray()) }
        } catch (_: Exception) { }
        try {
            openFileOutput(OUTPUT_FILE, MODE_PRIVATE).use { it.write((binding.outputView.text?.toString() ?: "").toByteArray()) }
        } catch (_: Exception) { }
        val sp = getSharedPreferences("prefs", MODE_PRIVATE)
        sp.edit()
            .putInt("exec_count", execCount)
            .putInt("md_count", mdCount)
            .apply()
    }

    private fun loadPersistedState() {
        // Load code
        readFileText(NOTEBOOK_FILE)?.let { setEditorText(it) }
        // Load output
        val out = readFileText(OUTPUT_FILE)
        if (!out.isNullOrBlank()) {
            binding.outputView.text = out
            binding.outputCard.visibility = View.VISIBLE
            outputVisible = true
            restoreEditorAboveOutput()
        } else {
            resetOutput()
        }
        val sp = getSharedPreferences("prefs", MODE_PRIVATE)
        execCount = sp.getInt("exec_count", 0)
        mdCount = sp.getInt("md_count", 0)
    }

    private fun readFileText(name: String): String? {
        return try {
            val f = File(filesDir, name)
            if (f.exists()) f.readText() else null
        } catch (_: Exception) { null }
    }

    companion object {
        private const val SAVED_CODE_FILE = "saved_code.py"
        private const val NOTEBOOK_FILE = "notebook.py"
        private const val OUTPUT_FILE = "output.txt"
    }
}
