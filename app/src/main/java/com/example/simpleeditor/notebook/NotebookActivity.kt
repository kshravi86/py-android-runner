package com.example.simpleeditor.notebook

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.simpleeditor.R
import com.example.simpleeditor.databinding.ActivityNotebookBinding
import com.google.android.material.color.DynamicColors
import io.noties.markwon.Markwon
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotebookActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotebookBinding
    private lateinit var adapter: NotebookAdapter
    private var execCount: Int = 0
    private var markwon: Markwon? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityNotebookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topBar)
        tintToolbarIcons()

        ensurePython()
        setupRecycler()
        setupChips()
        loadNotebook()
    }

    override fun onPause() {
        super.onPause()
        saveNotebook()
    }

    private fun tintToolbarIcons() {
        val white = ContextCompat.getColor(this, android.R.color.white)
        binding.topBar.post {
            val m = binding.topBar.menu
            for (i in 0 until m.size()) m.getItem(i).icon?.setTint(white)
            binding.topBar.overflowIcon?.setTint(white)
        }
    }

    private fun setupRecycler() {
        adapter = NotebookAdapter(
            onRunCode = { pos, code -> runCodeCell(pos, code) },
            onPreviewMd = { _, md -> val mk = markwon ?: Markwon.create(this).also { markwon = it }; mk.toMarkdown(md) },
            onDelete = { pos -> adapter.items.removeAt(pos); adapter.notifyItemRemoved(pos); saveNotebook() },
            onMoveUp = { pos -> moveCell(pos, -1) },
            onMoveDown = { pos -> moveCell(pos, +1) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        adapter.setHasStableIds(true)
        binding.recycler.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.onItemMove(from, to)
                return true
            }

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                saveNotebook()
            }
        })
        touchHelper.attachToRecyclerView(binding.recycler)
    }

    private fun moveCell(position: Int, delta: Int) {
        val newPos = (position + delta).coerceIn(0, adapter.items.lastIndex)
        if (newPos == position) return
        val item = adapter.items.removeAt(position)
        adapter.items.add(newPos, item)
        adapter.notifyItemMoved(position, newPos)
        saveNotebook()
    }

    private fun setupChips() {
        binding.chipRunAll.setOnClickListener { runAll() }
        binding.chipAddCode.setOnClickListener { addCodeCell() }
        binding.chipAddMd.setOnClickListener { addMdCell() }
        binding.chipReset.setOnClickListener { resetSession() }
        binding.chipClearOut.setOnClickListener { clearAllOutputs() }
        binding.chipExportPy.setOnClickListener { exportAsPy() }
        binding.chipExportMd.setOnClickListener { exportAsMd() }
        binding.fabRunFocused.setOnClickListener { runFocused() }
    }

    private fun addCodeCell() {
        adapter.items.add(NBCell.CodeCell(System.currentTimeMillis()))
        adapter.notifyItemInserted(adapter.items.lastIndex)
        saveNotebook()
    }

    private fun addMdCell() {
        adapter.items.add(NBCell.MarkdownCell(System.currentTimeMillis()))
        adapter.notifyItemInserted(adapter.items.lastIndex)
        saveNotebook()
    }

    private fun runFocused() {
        val lm = binding.recycler.layoutManager as LinearLayoutManager
        val pos = lm.findFirstCompletelyVisibleItemPosition().takeIf { it != -1 } ?: lm.findFirstVisibleItemPosition()
        if (pos in adapter.items.indices) {
            val item = adapter.items[pos]
            if (item is NBCell.CodeCell) {
                runCodeCell(pos, item.code)
            } else {
                Toast.makeText(this, "Focus a code cell", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runCodeCell(position: Int, code: String) {
        val t0 = System.currentTimeMillis()
        val out = try {
            val py = Python.getInstance()
            py.getModule("runner").callAttr("run_cell", code).toString()
        } catch (e: PyException) {
            e.message ?: e.toString()
        }
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val dur = System.currentTimeMillis() - t0
        execCount += 1
        val header = "In [${execCount}] • $timeStr • ${dur} ms\nOut [${execCount}]:"
        val cell = adapter.items[position] as NBCell.CodeCell
        cell.output = listOf(header, out.trimEnd()).joinToString("\n")
        adapter.notifyItemChanged(position)
        saveNotebook()
    }

    private fun clearAllOutputs() {
        adapter.items.forEach {
            if (it is NBCell.CodeCell) it.output = ""
            if (it is NBCell.MarkdownCell) it.preview = ""
        }
        adapter.notifyDataSetChanged()
        saveNotebook()
    }

    private fun runAll() {
        for (i in adapter.items.indices) {
            val item = adapter.items[i]
            if (item is NBCell.CodeCell) runCodeCell(i, item.code)
        }
    }

    private fun resetSession() {
        try {
            val py = Python.getInstance()
            py.getModule("runner").callAttr("reset_session")
            Toast.makeText(this, "Session reset", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {}
    }

    private fun exportAsPy() {
        val sb = StringBuilder()
        adapter.items.forEach {
            when (it) {
                is NBCell.MarkdownCell -> {
                    sb.append("# %% [markdown]\n")
                    it.text.lines().forEach { line -> sb.append("# ").append(line).append('\n') }
                }
                is NBCell.CodeCell -> {
                    sb.append("# %%\n")
                    sb.append(it.code.trimEnd()).append('\n')
                    if (it.output.isNotBlank()) {
                        sb.append("# Out:\n")
                        it.output.lines().forEach { l -> sb.append("# ").append(l).append('\n') }
                    }
                }
            }
            sb.append('\n')
        }
        shareText("notebook.py", sb.toString(), "text/x-python")
    }

    private fun exportAsMd() {
        val sb = StringBuilder()
        adapter.items.forEach {
            when (it) {
                is NBCell.MarkdownCell -> {
                    sb.append(it.text.trim()).append("\n\n")
                }
                is NBCell.CodeCell -> {
                    if (it.code.isNotBlank()) {
                        sb.append("```python\n").append(it.code.trimEnd()).append("\n````\n\n")
                    }
                    if (it.output.isNotBlank()) {
                        sb.append("````\n").append(it.output.trimEnd()).append("\n````\n\n")
                    }
                }
            }
        }
        shareText("notebook.md", sb.toString(), "text/markdown")
    }

    private fun shareText(filename: String, content: String, mime: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(android.content.Intent.EXTRA_SUBJECT, filename)
                .putExtra(android.content.Intent.EXTRA_TEXT, content)
            startActivity(android.content.Intent.createChooser(intent, "Share notebook"))
        } catch (_: Throwable) {}
    }

    private fun ensurePython() {
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
    }

    private fun saveNotebook() {
        try {
            val arr = JSONArray()
            adapter.items.forEach {
                when (it) {
                    is NBCell.CodeCell -> arr.put(JSONObject().apply {
                        put("type", "code"); put("id", it.id); put("code", it.code); put("output", it.output)
                    })
                    is NBCell.MarkdownCell -> arr.put(JSONObject().apply {
                        put("type", "md"); put("id", it.id); put("text", it.text); put("preview", it.preview)
                    })
                }
            }
            File(filesDir, FILE).writeText(arr.toString())
        } catch (_: Exception) {}
    }

    private fun loadNotebook() {
        val f = File(filesDir, FILE)
        if (!f.exists()) {
            // Seed with 5 empty code cells by default
            repeat(5) { adapter.items.add(NBCell.CodeCell(System.currentTimeMillis() + it)) }
            adapter.notifyDataSetChanged()
            saveNotebook()
            return
        }
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                when (o.getString("type")) {
                    "code" -> adapter.items.add(NBCell.CodeCell(o.getLong("id"), o.optString("code"), o.optString("output")))
                    "md" -> adapter.items.add(NBCell.MarkdownCell(o.getLong("id"), o.optString("text"), o.optString("preview")))
                }
            }
            adapter.notifyDataSetChanged()
        } catch (_: Exception) {
            // If loading fails, seed with 5 empty code cells
            adapter.items.clear()
            repeat(5) { adapter.items.add(NBCell.CodeCell(System.currentTimeMillis() + it)) }
            adapter.notifyDataSetChanged()
            saveNotebook()
        }
    }

    companion object { private const val FILE = "notebook_cells.json" }
}
