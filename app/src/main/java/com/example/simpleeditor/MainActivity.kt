package com.example.simpleeditor

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.simpleeditor.databinding.ActivityMainBinding
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import java.nio.charset.StandardCharsets
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var outputVisible: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        ensurePython()
        setupCodeEditor()
        setupDefaultCode(savedInstanceState)
        setupToolbarActions()
    }

    private fun ensurePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }

    private fun setupDefaultCode(savedInstanceState: Bundle?) {
        val saved = readSavedCode()
        if (savedInstanceState == null) {
            val starterCode = if (!saved.isNullOrBlank()) saved else getString(R.string.default_code)
            setEditorText(starterCode)
            displayResult(runPythonCode(starterCode))
        } else if (binding.outputView.text.isNullOrBlank()) {
            resetOutput()
        }
    }

    private fun setupToolbarActions() {
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_run -> {
                    val code = getEditorText()
                    val result = runPythonCode(code)
                    displayResult(result)
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
        outputVisible = !outputVisible
        binding.outputCard.visibility = if (outputVisible) View.VISIBLE else View.GONE
        val root = binding.root as ConstraintLayout
        val set = ConstraintSet()
        set.clone(root)
        if (outputVisible) {
            set.connect(binding.editorCard.id, ConstraintSet.BOTTOM, binding.outputCard.id, ConstraintSet.TOP)
            set.connect(binding.outputCard.id, ConstraintSet.TOP, binding.editorCard.id, ConstraintSet.BOTTOM)
            set.connect(binding.outputCard.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        } else {
            set.clear(binding.editorCard.id, ConstraintSet.BOTTOM)
            set.connect(binding.editorCard.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        }
        set.applyTo(root)
    }

    // Inline buttons removed to maximize editor space. Use toolbar actions instead.

    private fun resetOutput() {
        binding.outputView.text = getString(R.string.empty_output)
    }

    private fun displayResult(result: String) {
        val text = result.ifBlank { getString(R.string.empty_output) }
        binding.outputView.text = text
        binding.outputScroll.post { binding.outputScroll.fullScroll(View.FOCUS_DOWN) }
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
        binding.editorView.setTextSize(14f)
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

    companion object {
        private const val SAVED_CODE_FILE = "saved_code.py"
    }
}
