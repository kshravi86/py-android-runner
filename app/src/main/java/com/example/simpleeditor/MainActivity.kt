package com.example.simpleeditor

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.simpleeditor.databinding.ActivityMainBinding

private const val PREFS_NAME = "editor"
private const val PREF_KEY_CONTENT = "content"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        binding.editorView.setText(preferences.getString(PREF_KEY_CONTENT, ""))

        binding.saveButton.setOnClickListener {
            val text = binding.editorView.text?.toString().orEmpty()
            preferences.edit().putString(PREF_KEY_CONTENT, text).apply()
            Toast.makeText(this, R.string.saved_message, Toast.LENGTH_SHORT).show()
        }

        binding.clearButton.setOnClickListener {
            binding.editorView.text?.clear()
            preferences.edit().remove(PREF_KEY_CONTENT).apply()
            Toast.makeText(this, R.string.cleared_message, Toast.LENGTH_SHORT).show()
        }
    }
}
