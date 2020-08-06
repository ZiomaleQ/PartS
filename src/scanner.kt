internal class Scanner(private val source: String) {
    private val tokens: MutableList<Token> = mutableListOf()
    private var start = 0
    private var current = 0
    private var line = 1

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current
            scanToken()
        }
        tokens.add(Token("EOF", "", null, line))
        return tokens
    }

    private fun scanToken() {
        when (advance()) {
            '(' -> addToken("LEFT_PAREN")
            ')' -> addToken("RIGHT_PAREN")
            '{' -> addToken("LEFT_BRACE")
            '}' -> addToken("RIGHT_BRACE")
            ',' -> addToken("COMMA")
            '.' -> addToken("DOT")
            '-' -> addToken("MINUS")
            '+' -> addToken("PLUS")
            ';' -> addToken("SEMICOLON")
            '*' -> addToken("STAR")
            '!' -> addToken(if (match('=')) "BANG_EQUAL" else "BANG")
            '=' -> addToken(if (match('=')) "EQUAL_EQUAL" else "EQUAL")
            '<' -> addToken(if (match('=')) "LESS_EQUAL" else "LESS")
            '>' -> addToken(if (match('=')) "GREATER_EQUAL" else "GREATER")
            '/' -> when {
                match('/') -> while (peek() != '\n' && !isAtEnd()) advance()
                match('*') -> while (peek() != '*' && peekNext() != '\\' && !isAtEnd()) advance()
                else -> addToken("SLASH")
            }
            ' ', '\r', '\t' -> null
            '\n' -> line++
            '"' -> string()
            else -> when {
                isDigit(previous()) -> number()
                isAlpha(previous()) -> identifier()
                else -> PartS.error(line, "Unexpected character.")
            }
        }
    }

    private fun advance(): Char {
        current++
        return source[current - 1]
    }

    private fun addToken(type: String) = addToken(type, null)
    private fun addToken(type: String, literal: Any?) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, line))
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        current++
        return true
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }

        // Unterminated string.
        if (isAtEnd()) {
            PartS.error(line, "Unterminated string.")
            return
        }

        // The closing ".
        advance()

        // Trim the surrounding quotes.
        val value = source.substring(start + 1, current - 1)
        addToken("STRING", value)
    }

    private fun number() {
        while (isDigit(peek())) advance()

        // Look for a fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance()
            while (isDigit(peek())) advance()
        }
        addToken("NUMBER", source.substring(start, current).toDouble())
    }

    private fun identifier() {
        while (isAlphaNumeric(peek())) advance()
        val text = source.substring(start, current)
        addToken(
            listOf(
                "and", "class", "else", "false",
                "for", "fun", "if", "let",
                "nil", "return", "super", "this",
                "true", "while"
            ).find{it == text}?.toUpperCase() ?: "IDENTIFIER", text
        )
    }

    private fun isAlpha(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_'
    private fun isAlphaNumeric(c: Char): Boolean = isAlpha(c) || isDigit(c)
    private fun isDigit(c: Char): Boolean = c in '0'..'9'
    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]
    private fun previous(): Char = if (current >= source.length - 1) '\u0000' else source[current - 1]
    private fun peekNext(): Char = if (current + 1 >= source.length) '\u0000' else source[current + 1]
    private fun isAtEnd(): Boolean = current >= source.length
}