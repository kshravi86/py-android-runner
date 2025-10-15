package com.example.simpleeditor

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.simpleeditor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ensurePython()
        resetOutput()

        binding.runButton.setOnClickListener {
            val code = binding.editorView.text?.toString().orEmpty()
            val result = runPythonCode(code)
            binding.outputView.text = if (result.isBlank()) getString(R.string.empty_output) else result
            binding.outputScroll.post { binding.outputScroll.fullScroll(View.FOCUS_DOWN) }
        }

        binding.clearButton.setOnClickListener {
            binding.editorView.text?.clear()
            resetOutput()
        }
    }

    private fun ensurePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }

    private fun resetOutput() {
        binding.outputView.text = getString(R.string.empty_output)
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
}
