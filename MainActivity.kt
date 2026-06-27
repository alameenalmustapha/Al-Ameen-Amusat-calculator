package com.example.calculator

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.gridlayout.widget.GridLayout
import net.objecthunter.exp4j.ExpressionBuilder
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    // ── UI refs ──────────────────────────────────────────────────────────────
    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView
    private lateinit var sciPanel: GridLayout
    private lateinit var btnToggleSci: Button
    private lateinit var btnToggleRad: Button

    // ── State ────────────────────────────────────────────────────────────────
    private val expression = StringBuilder()
    private var isScientificVisible = false
    private var isRadianMode = true          // true = Rad, false = Deg
    private var justEvaluated = false        // after "=" pressed, next digit clears
    private var openParens = 0               // track bracket balance for ( ) toggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Display
        tvExpression = findViewById(R.id.tvExpression)
        tvResult = findViewById(R.id.tvResult)

        // Scientific panel
        sciPanel = findViewById(R.id.sciPanel)
        btnToggleSci = findViewById(R.id.btnToggleSci)
        btnToggleRad = findViewById(R.id.btnToggleRad)

        setupListeners()
    }

    // ── Wire up all buttons ───────────────────────────────────────────────────
    private fun setupListeners() {

        // Numbers
        mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9", R.id.btn00 to "00", R.id.btnDot to "."
        ).forEach { (id, value) ->
            findViewById<Button>(id).setOnClickListener { appendDigit(value) }
        }

        // Operators
        mapOf(
            R.id.btnAdd to "+", R.id.btnSub to "-",
            R.id.btnMul to "*", R.id.btnDiv to "/"
        ).forEach { (id, op) ->
            findViewById<Button>(id).setOnClickListener { appendOperator(op) }
        }

        // Special
        findViewById<Button>(R.id.btnClear).setOnClickListener { clearAll() }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener { backspace() }
        findViewById<Button>(R.id.btnEquals).setOnClickListener { evaluate() }
        findViewById<Button>(R.id.btnPercent).setOnClickListener { appendPercent() }
        findViewById<Button>(R.id.btnParens).setOnClickListener { appendParens() }

        // Scientific toggle panel
        btnToggleSci.setOnClickListener { toggleScientific() }
        btnToggleRad.setOnClickListener { toggleAngleMode() }

        // Scientific functions
        mapOf(
            R.id.btnSin to "sin", R.id.btnCos to "cos", R.id.btnTan to "tan",
            R.id.btnLn to "ln", R.id.btnLog to "log10",
            R.id.btnSqrt to "sqrt", R.id.btnCbrt to "cbrt"
        ).forEach { (id, fn) ->
            findViewById<Button>(id).setOnClickListener { appendFunction(fn) }
        }

        findViewById<Button>(R.id.btnPi).setOnClickListener { appendConstant("π", Math.PI.toString()) }
        findViewById<Button>(R.id.btnE).setOnClickListener { appendConstant("e", Math.E.toString()) }
        findViewById<Button>(R.id.btnPhi).setOnClickListener {
            appendConstant("φ", ((1 + sqrt(5.0)) / 2).toString())
        }
        findViewById<Button>(R.id.btnPow).setOnClickListener { appendRaw("^") }
        findViewById<Button>(R.id.btnFactorial).setOnClickListener { applyFactorial() }
    }

    // ── Input helpers ─────────────────────────────────────────────────────────

    private fun appendDigit(d: String) {
        if (justEvaluated) {
            // Start fresh on new number after "="
            if (d != ".") { expression.clear(); openParens = 0 }
            justEvaluated = false
        }
        if (d == "." && currentTokenHasDot()) return
        expression.append(d)
        updateDisplay()
    }

    private fun appendOperator(op: String) {
        justEvaluated = false
        val s = expression.toString()
        if (s.isEmpty() || s == "-") return
        // Replace trailing operator if present
        if (s.isNotEmpty() && s.last() in listOf('+', '-', '*', '/', '^')) {
            expression.deleteCharAt(expression.length - 1)
        }
        expression.append(op)
        updateDisplay()
    }

    private fun appendRaw(token: String) {
        justEvaluated = false
        expression.append(token)
        updateDisplay()
    }

    private fun appendFunction(fn: String) {
        justEvaluated = false
        expression.append("$fn(")
        openParens++
        updateDisplay()
    }

    private fun appendConstant(display: String, value: String) {
        justEvaluated = false
        // We store the numeric value so exp4j can evaluate, but show symbol
        expression.append(value)
        updateDisplay(displayOverride = expression.toString()
            .replace(Math.PI.toString(), "π")
            .replace(Math.E.toString(), "e")
            .replace(((1 + sqrt(5.0)) / 2).toString(), "φ"))
        return
    }

    private fun appendPercent() {
        val s = expression.toString()
        if (s.isEmpty()) return
        val value = s.toDoubleOrNull() ?: return
        expression.clear()
        expression.append(value / 100.0)
        updateDisplay()
    }

    private fun appendParens() {
        justEvaluated = false
        val s = expression.toString()
        val lastChar = if (s.isEmpty()) null else s.last()
        if (openParens == 0 || lastChar == null || lastChar in listOf('+', '-', '*', '/', '^', '(')) {
            expression.append("(")
            openParens++
        } else {
            expression.append(")")
            openParens--
        }
        updateDisplay()
    }

    private fun applyFactorial() {
        val s = expression.toString()
        val num = s.toDoubleOrNull() ?: tvResult.text.toString().toDoubleOrNull() ?: return
        val n = num.toInt()
        val result = factorial(n)
        expression.clear()
        expression.append(result.toLong())
        tvResult.text = formatResult(result.toDouble())
        tvExpression.text = "$n! ="
        justEvaluated = true
        updateDisplay()
    }

    private fun clearAll() {
        expression.clear()
        openParens = 0
        justEvaluated = false
        tvExpression.text = ""
        tvResult.text = "0"
    }

    private fun backspace() {
        if (justEvaluated) { clearAll(); return }
        if (expression.isNotEmpty()) {
            val last = expression.last()
            if (last == '(') openParens--
            if (last == ')') openParens++
            expression.deleteCharAt(expression.length - 1)
        }
        updateDisplay()
        if (expression.isEmpty()) tvResult.text = "0"
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    private fun evaluate() {
        val raw = expression.toString()
        if (raw.isEmpty()) return

        // Close unclosed parens
        val closed = raw + ")".repeat(openParens)

        val result = computeExpression(closed)
        tvExpression.text = "${buildDisplayString()} ="
        tvResult.text = formatResult(result)

        if (!result.isNaN() && !result.isInfinite()) {
            expression.clear()
            expression.append(formatResultRaw(result))
            openParens = 0
        }
        justEvaluated = true
    }

    private fun computeExpression(raw: String): Double {
        return try {
            // Replace ^ with pow notation for exp4j
            val expr = raw.replace("^", "^")

            // Handle trig with angle conversion
            val processed = if (!isRadianMode) {
                // Wrap trig functions to convert degrees to radians
                raw
                    .replace(Regex("sin\\(")) { "sin(PI/180*(" }
                    .replace(Regex("cos\\(")) { "cos(PI/180*(" }
                    .replace(Regex("tan\\(")) { "tan(PI/180*(" }
            } else raw

            val e = ExpressionBuilder(processed)
                .variables("PI")
                .build()
                .setVariable("PI", Math.PI)
            e.evaluate()
        } catch (e: Exception) {
            Double.NaN
        }
    }

    // ── Display ───────────────────────────────────────────────────────────────

    private fun updateDisplay(displayOverride: String? = null) {
        val display = displayOverride ?: buildDisplayString()
        tvExpression.text = display
        // Live preview result
        val raw = expression.toString()
        if (raw.isNotEmpty() && !raw.last().let { it == '+' || it == '-' || it == '*' || it == '/' || it == '(' || it == '^' }) {
            val preview = computeExpression(raw + ")".repeat(openParens))
            if (!preview.isNaN()) tvResult.text = formatResult(preview)
        }
    }

    private fun buildDisplayString(): String =
        expression.toString()
            .replace(Math.PI.toString(), "π")
            .replace(Math.E.toString(), "e")
            .replace("*", "×")
            .replace("/", "÷")
            .replace("sqrt(", "√(")
            .replace("cbrt(", "³√(")
            .replace("log10(", "log(")

    private fun formatResult(value: Double): String {
        if (value.isNaN()) return "Error"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        return if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15)
            value.toLong().toString()
        else {
            val s = "%.10g".format(value).trimEnd('0').trimEnd('.')
            s
        }
    }

    private fun formatResultRaw(value: Double): String =
        if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15)
            value.toLong().toString()
        else "%.10g".format(value).trimEnd('0').trimEnd('.')

    // ── Toggle helpers ────────────────────────────────────────────────────────

    private fun toggleScientific() {
        isScientificVisible = !isScientificVisible
        sciPanel.visibility = if (isScientificVisible) View.VISIBLE else View.GONE
    }

    private fun toggleAngleMode() {
        isRadianMode = !isRadianMode
        btnToggleRad.text = if (isRadianMode) "Rad" else "Deg"
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private fun currentTokenHasDot(): Boolean {
        val s = expression.toString()
        val lastOpIndex = s.indexOfLast { it in listOf('+', '-', '*', '/', '(') }
        val currentToken = s.substring(lastOpIndex + 1)
        return currentToken.contains('.')
    }

    private fun factorial(n: Int): Double {
        if (n < 0) return Double.NaN
        if (n == 0 || n == 1) return 1.0
        var result = 1.0
        for (i in 2..n) result *= i
        return result
    }
}
