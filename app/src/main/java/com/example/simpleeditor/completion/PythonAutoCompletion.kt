package com.example.simpleeditor.completion

import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import com.example.simpleeditor.R
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.completion.SimpleSnippetCompletionItem
import io.github.rosemoe.sora.lang.completion.SnippetDescription
import io.github.rosemoe.sora.lang.completion.snippet.CodeSnippet
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion

class PythonAutoCompletion(editor: CodeEditor) : EditorAutoCompletion(editor) {

    private val keywords = listOf(
        "and","as","assert","break","class","continue","def","del","elif","else",
        "except","False","finally","for","from","global","if","import","in","is",
        "lambda","None","nonlocal","not","or","pass","raise","return","True","try",
        "while","with","yield"
    )

    override fun requireCompletion() {
        if (!isEnabled) return
        val text: Content = editor.text
        val cur = text.cursor
        if (cur.isSelected) {
            hide()
            return
        }

        val line = cur.leftLine
        val col = cur.leftColumn
        val prefix = currentPrefix(text, line, col)

        val items = mutableListOf<CompletionItem>()

        // Keywords
        for (kw in keywords) {
            if (prefix.isEmpty() || kw.startsWith(prefix)) {
                val item = SimpleCompletionItem(prefix.length, kw)
                    .label(kw)
                    .desc("Keyword")
                    .kind(CompletionItemKind.Keyword)
                items.add(item)
            }
        }

        // Snippets (basic)
        items.addAll(buildSnippets(prefix))

        if (items.isEmpty()) {
            hide()
            return
        }

        // Attach to adapter and display
        adapter.attachValues(this, items)
        adapter.notifyDataSetInvalidated()
        updateCompletionWindowPosition()
        val desired = (adapter.itemHeight * adapter.count).coerceAtMost(maxHeight)
        setSize(width, desired)
        if (!isShowing) show()
    }

    private fun currentPrefix(text: Content, line: Int, column: Int): String {
        val part = text.getLineString(line).substring(0, column.coerceAtMost(text.getColumnCount(line)))
        var i = part.length - 1
        while (i >= 0) {
            val c = part[i]
            if (!Character.isLetterOrDigit(c) && c != '_') break
            i--
        }
        return part.substring(i + 1)
    }

    private fun buildSnippets(prefix: String): List<CompletionItem> {
        fun matches(trigger: String) = prefix.isEmpty() || trigger.startsWith(prefix)
        val list = mutableListOf<CompletionItem>()

        fun snippetItem(trigger: String, label: String, desc: String, builder: CodeSnippet.Builder): CompletionItem? {
            if (!matches(trigger)) return null
            val snippet = builder.build()
            val sd = SnippetDescription(0, snippet, false)
            val item = SimpleSnippetCompletionItem(label, desc, sd)
            item.kind(CompletionItemKind.Snippet)
            return item
        }

        // def function
        snippetItem("def", "def …:", "Function", CodeSnippet.Builder()
            .addPlainText("def ")
            .addPlaceholder(1, "name")
            .addPlainText("(")
            .addPlaceholder(2, "args")
            .addPlainText("):\n    ")
            .addPlaceholder(0, "pass")
        )?.let { list.add(it) }

        // class
        snippetItem("class", "class …:", "Class", CodeSnippet.Builder()
            .addPlainText("class ")
            .addPlaceholder(1, "Name")
            .addPlainText(":\n    ")
            .addPlaceholder(0, "pass")
        )?.let { list.add(it) }

        // if
        snippetItem("if", "if …:", "If block", CodeSnippet.Builder()
            .addPlainText("if ")
            .addPlaceholder(1, "condition")
            .addPlainText(":\n    ")
            .addPlaceholder(0, "pass")
        )?.let { list.add(it) }

        // for
        snippetItem("for", "for … in …:", "For loop", CodeSnippet.Builder()
            .addPlainText("for ")
            .addPlaceholder(1, "item")
            .addPlainText(" in ")
            .addPlaceholder(2, "iterable")
            .addPlainText(":\n    ")
            .addPlaceholder(0, "pass")
        )?.let { list.add(it) }

        // while
        snippetItem("while", "while …:", "While loop", CodeSnippet.Builder()
            .addPlainText("while ")
            .addPlaceholder(1, "condition")
            .addPlainText(":\n    ")
            .addPlaceholder(0, "pass")
        )?.let { list.add(it) }

        // try/except
        snippetItem("try", "try/except", "Exception handler", CodeSnippet.Builder()
            .addPlainText("try:\n    ")
            .addPlaceholder(1, "pass")
            .addPlainText("\nexcept ")
            .addPlaceholder(2, "Exception")
            .addPlainText(":\n    ")
            .addPlaceholder(0, "pass")
        )?.let { list.add(it) }

        // with
        snippetItem("with", "with … as …:", "Context manager", CodeSnippet.Builder()
            .addPlainText("with ")
            .addPlaceholder(1, "expr")
            .addPlainText(" as ")
            .addPlaceholder(2, "var")
            .addPlainText(":\n    ")
            .addPlaceholder(0, "pass")
        )?.let { list.add(it) }

        // import
        snippetItem("import", "import …", "Import module", CodeSnippet.Builder()
            .addPlainText("import ")
            .addPlaceholder(0, "module")
        )?.let { list.add(it) }

        // from import
        snippetItem("from", "from … import …", "From import", CodeSnippet.Builder()
            .addPlainText("from ")
            .addPlaceholder(1, "module")
            .addPlainText(" import ")
            .addPlaceholder(0, "name")
        )?.let { list.add(it) }

        // print
        snippetItem("print", "print(…)", "Print", CodeSnippet.Builder()
            .addPlainText("print(")
            .addPlaceholder(0, "value")
            .addPlainText(")")
        )?.let { list.add(it) }

        return list
    }
}

