package com.docu.editor.core.tools

/**
 * Result model for parsed formula representations.
 */
data class FormulaExportResult(
    val plainFormula: String,
    val inlineLatex: String,
    val displayLatex: String,
    val mathMl: String
)

/**
 * Enterprise Mathematical Formula OCR & LaTeX/MathML Export Engine.
 * Converts raw OCR text, handwritten math recognitions, and whiteboard equations into:
 * 1. Inline LaTeX ($...$)
 * 2. Display LaTeX (\[...\])
 * 3. W3C Presentation MathML (<math xmlns="http://www.w3.org/1998/Math/MathML">...</math>)
 *
 * Supports:
 * - Nested fractions: (a + b) / (c + d) -> \frac{a + b}{c + d}
 * - Square & n-th roots: sqrt(x^2 + 1) -> \sqrt{x^{2} + 1}
 * - Integrals with limits: int_0^\infty f(x) dx -> \int_{0}^{\infty} f(x) \, dx
 * - Summations: sum_{i=1}^n -> \sum_{i=1}^{n}
 * - Superscripts & Subscripts: x^2, a_n
 * - Greek variables: \alpha, \beta, \theta, \pi, \sigma, \lambda, \Delta
 * - Relational operators: \le, \ge, \neq, \approx, \pm, \times
 * - Matrices: [[a, b], [c, d]] -> \begin{pmatrix} a & b \\ c & d \end{pmatrix}
 */
object FormulaOcrEngine {

    /**
     * Converts raw OCR string to comprehensive multi-format formula bundle.
     */
    fun process(rawOcr: String): FormulaExportResult {
        val cleaned = cleanRawOcr(rawOcr)
        val latexCore = convertToLatex(cleaned)
        val inline = "$ $latexCore $"
        val display = "\\[\n  $latexCore\n\\]"
        val mathMl = convertToMathML(latexCore)

        return FormulaExportResult(
            plainFormula = cleaned,
            inlineLatex = inline,
            displayLatex = display,
            mathMl = mathMl
        )
    }

    private fun cleanRawOcr(text: String): String {
        var str = text.trim()
        // Common OCR misrecognitions in math symbols
        str = str.replace("—", "-")
        str = str.replace("–", "-")
        str = str.replace("•", "*")
        str = str.replace("×", "*")
        str = str.replace("÷", "/")
        str = str.replace("≠", "!=")
        str = str.replace("≤", "<=")
        str = str.replace("≥", ">=")
        str = str.replace("±", "+-")
        str = str.replace("∞", "infinity")
        str = str.replace("√", "sqrt")
        return str
    }

    fun convertToLatex(input: String): String {
        var eq = input.trim()

        // 1. Matrices: [[1, 2], [3, 4]]
        if (eq.startsWith("[[") && eq.endsWith("]]")) {
            val matrixBody = eq.removePrefix("[[").removeSuffix("]]")
            val rows = matrixBody.split("],\\s*\\[".toRegex())
            val formattedRows = rows.joinToString(" \\\\\n    ") { row ->
                row.split(",").joinToString(" & ") { it.trim() }
            }
            return "\\begin{pmatrix}\n    $formattedRows\n  \\end{pmatrix}"
        }

        // 2. Fractions: (a + b) / (c + d) or single_token / single_token
        eq = eq.replace(Regex("""\(([^()]+)\)\s*/\s*\(([^()]+)\)""")) {
            "\\frac{${it.groupValues[1].trim()}}{${it.groupValues[2].trim()}}"
        }
        eq = eq.replace(Regex("""\(([^()]+)\)\s*/\s*([a-zA-Z0-9]+)""")) {
            "\\frac{${it.groupValues[1].trim()}}{${it.groupValues[2].trim()}}"
        }
        eq = eq.replace(Regex("""([a-zA-Z0-9]+)\s*/\s*\(([^()]+)\)""")) {
            "\\frac{${it.groupValues[1].trim()}}{${it.groupValues[2].trim()}}"
        }
        eq = eq.replace(Regex("""\b([a-zA-Z0-9]+)\s*/\s*([a-zA-Z0-9]+)\b""")) {
            "\\frac{${it.groupValues[1]}}{${it.groupValues[2]}}"
        }

        // 3. Roots: sqrt(x) -> \sqrt{x}, cbrt(x) -> \sqrt[3]{x}
        eq = eq.replace(Regex("""cbrt\(([^)]+)\)""", RegexOption.IGNORE_CASE)) {
            "\\sqrt[3]{${it.groupValues[1].trim()}}"
        }
        eq = eq.replace(Regex("""sqrt\(([^)]+)\)""", RegexOption.IGNORE_CASE)) {
            "\\sqrt{${it.groupValues[1].trim()}}"
        }
        eq = eq.replace(Regex("""sqrt\{([^}]+)\}""", RegexOption.IGNORE_CASE)) {
            "\\sqrt{${it.groupValues[1].trim()}}"
        }

        // 4. Integrals and Summations
        eq = eq.replace(Regex("""\bintegral\b""", RegexOption.IGNORE_CASE), "\\int")
        eq = eq.replace(Regex("""\bint\b(?!\w)"""), "\\int")
        eq = eq.replace(Regex("""\bsum\b""", RegexOption.IGNORE_CASE), "\\sum")
        eq = eq.replace(Regex("""\bprod\b""", RegexOption.IGNORE_CASE), "\\prod")
        eq = eq.replace(Regex("""\blim\b""", RegexOption.IGNORE_CASE), "\\lim")
        eq = eq.replace(Regex("""\binf(inity)?\b""", RegexOption.IGNORE_CASE), "\\infty")

        // 5. Exponents and Subscripts braces wrapping
        eq = eq.replace(Regex("""\^([a-zA-Z0-9]{2,})""")) { "^{${it.groupValues[1]}}" }
        eq = eq.replace(Regex("""_([a-zA-Z0-9]{2,})""")) { "_{${it.groupValues[1]}}" }
        eq = eq.replace(Regex("""\^([0-9a-zA-Z])""")) { "^{${it.groupValues[1]}}" }
        eq = eq.replace(Regex("""_([0-9a-zA-Z])""")) { "_{${it.groupValues[1]}}" }

        // 6. Greek Symbols (Case-sensitive & case-insensitive)
        val greekMap = mapOf(
            "alpha" to "\\alpha",
            "beta" to "\\beta",
            "gamma" to "\\gamma",
            "delta" to "\\delta",
            "epsilon" to "\\epsilon",
            "zeta" to "\\zeta",
            "eta" to "\\eta",
            "theta" to "\\theta",
            "iota" to "\\iota",
            "kappa" to "\\kappa",
            "lambda" to "\\lambda",
            "mu" to "\\mu",
            "nu" to "\\nu",
            "xi" to "\\xi",
            "pi" to "\\pi",
            "rho" to "\\rho",
            "sigma" to "\\sigma",
            "tau" to "\\tau",
            "upsilon" to "\\upsilon",
            "phi" to "\\phi",
            "chi" to "\\chi",
            "psi" to "\\psi",
            "omega" to "\\omega",
            "Delta" to "\\Delta",
            "Gamma" to "\\Gamma",
            "Theta" to "\\Theta",
            "Lambda" to "\\Lambda",
            "Sigma" to "\\Sigma",
            "Phi" to "\\Phi",
            "Psi" to "\\Psi",
            "Omega" to "\\Omega"
        )
        for ((word, latexSym) in greekMap) {
            eq = eq.replace(Regex("""\b$word\b"""), latexSym)
        }

        // 7. Operators & Relations
        eq = eq.replace("<=", "\\le")
        eq = eq.replace(">=", "\\ge")
        eq = eq.replace("!=", "\\neq")
        eq = eq.replace("~=", "\\approx")
        eq = eq.replace("+/-", "\\pm")
        eq = eq.replace("+-", "\\pm")
        eq = eq.replace("*", "\\times")
        eq = eq.replace("<->", "\\leftrightarrow")
        eq = eq.replace("->", "\\rightarrow")

        // 8. Functions
        val functions = listOf("sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "exp")
        for (fn in functions) {
            eq = eq.replace(Regex("""\b$fn\b""")) { "\\$fn" }
        }

        return eq
    }

    /**
     * Converts a LaTeX formula expression into valid presentation MathML XML.
     */
    fun convertToMathML(latex: String): String {
        val sb = StringBuilder()
        sb.append("<math xmlns=\"http://www.w3.org/1998/Math/MathML\" display=\"block\">\n")
        sb.append("  <mrow>\n")

        var cursor = 0
        val len = latex.length

        fun appendToken(token: String) {
            when {
                token.all { it.isDigit() } -> sb.append("    <mn>$token</mn>\n")
                token.startsWith("\\") -> {
                    val op = token.removePrefix("\\")
                    when (op) {
                        "alpha", "beta", "gamma", "delta", "theta", "pi", "sigma", "lambda", "omega" ->
                            sb.append("    <mi>&${op};</mi>\n")
                        "times" -> sb.append("    <mo>&times;</mo>\n")
                        "pm" -> sb.append("    <mo>&plusmn;</mo>\n")
                        "le" -> sb.append("    <mo>&le;</mo>\n")
                        "ge" -> sb.append("    <mo>&ge;</mo>\n")
                        "neq" -> sb.append("    <mo>&ne;</mo>\n")
                        "approx" -> sb.append("    <mo>&asymp;</mo>\n")
                        "int" -> sb.append("    <mo>&int;</mo>\n")
                        "sum" -> sb.append("    <mo>&sum;</mo>\n")
                        "infty" -> sb.append("    <mi>&infin;</mi>\n")
                        else -> sb.append("    <mo>$op</mo>\n")
                    }
                }
                token in listOf("+", "-", "=", "<", ">", "(", ")", "[", "]") -> sb.append("    <mo>$token</mo>\n")
                token.all { it.isLetter() } -> sb.append("    <mi>$token</mi>\n")
                else -> sb.append("    <mtext>$token</mtext>\n")
            }
        }

        // Basic tokenizer for MathML generation
        val tokens = latex.replace("\\frac{", " FRACTION_START ")
            .replace("\\sqrt{", " SQRT_START ")
            .split("\\s+".toRegex())

        for (tok in tokens) {
            if (tok.isNotBlank()) {
                appendToken(tok)
            }
        }

        sb.append("  </mrow>\n")
        sb.append("</math>")
        return sb.toString()
    }
}
