import Expr.*
import Stmt.Return
import Stmt.While
import java.util.*


class Parser(private val tokens: List<Token>) {
    private class ParseError : RuntimeException()

    private var current = 0

    fun parse(): List<Stmt> {
        val statements: MutableList<Stmt> = ArrayList()
        while (!isAtEnd()) declaration().let { if (it != null) statements.add(it); }
        return statements
    }

    private fun declaration(): Stmt? {
        return try {
            return when (peek().type) {
                "CLASS" -> classDeclaration()
                "FUN" -> function("function", true)
                "LET" -> varDeclaration()
                else -> statement()
            }
        } catch (error: ParseError) {
            synchronize()
            null
        }
    }

    private fun classDeclaration(): Stmt {
        val name = consume("IDENTIFIER" to "Expect class name.", advance = true)
        val superclass: Variable? = if (match("LESS")) {
            consume("IDENTIFIER" to "Expect superclass name.")
            Variable(previous())
        } else null
        consume("LEFT_BRACE" to "Expect '{' before class body.")
        val methods: MutableList<Stmt.Function?> = ArrayList()
        while (!check("RIGHT_BRACE") && !isAtEnd()) methods.add(function("method"))
        consume("RIGHT_BRACE" to "Expect '}' after class body.")
        return Stmt.Class(name, superclass, methods)
    }

    private fun function(kind: String, advance: Boolean = false): Stmt.Function? {
        if (advance) advance()
        val name = consume("IDENTIFIER" to "Expect $kind name.")

        consume("LEFT_PAREN" to "Expect '(' after $kind name.")
        val parameters: MutableList<Token?> = ArrayList()
        if (!check("RIGHT_PAREN")) {
            do {
                if (parameters.size >= 255) error(peek(), "Cannot have more than 255 parameters.")
                parameters.add(consume("IDENTIFIER" to "Expect parameter name."))
            } while (match("COMMA"))
        }
        consume("RIGHT_PAREN" to "Expect ')' after parameters.", "LEFT_BRACE" to "Expect '{' before $kind body.")
        return Stmt.Function(name, parameters, block())
    }

    private fun forStatement(): Stmt {
        consume("LEFT_PAREN" to "Expect '(' after 'for'.", advance = true)

        val initializer: Stmt? = when (peek().type) {
            "SEMICOLON" -> null
            "LET" -> varDeclaration()
            else -> expressionStatement()
        }

        val condition: Expr? = if (!check("SEMICOLON")) expression() else Literal(true)
        consume("SEMICOLON" to "Expect ';' after loop condition.")

        val increment: Expr? = if (!check("RIGHT_PAREN")) expression() else null
        consume("RIGHT_PAREN" to "Expect ')' after for clauses.")

        var body = if (increment == null) statement() else Stmt.Block(listOf(statement(), Stmt.Expression(increment)))

        body = if (initializer == null) While(condition, body, false) else Stmt.Block(
            listOf(initializer, While(condition, body, false))
        )

        return body
    }

    private fun returnStatement(): Stmt {
        val keyword = advance()
        val value: Expr? = if (!check("SEMICOLON")) expression() else null
        consume("SEMICOLON" to "Expect ';' after return value." + previous())
        return Return(keyword, value)
    }


    private fun whileStatement(): Stmt {
        consume("LEFT_PAREN" to "Expect '(' after 'while'.", advance = true)
        val condition = expression()
        consume("RIGHT_PAREN" to "Expect ')' after condition.")
        return While(condition, statement(), false)
    }

    private fun block(advance: Boolean = false): List<Stmt?> {
        if (advance) advance()
        val statements: MutableList<Stmt?> = ArrayList()
        while (!check("RIGHT_BRACE") && !isAtEnd()) statements.add(declaration())
        consume("RIGHT_BRACE" to "Expect '}' after block.")
        return statements
    }

    private fun ifStatement(): Stmt {
        advance()
        consume("LEFT_PAREN" to "Expect '(' after 'if'.")
        val condition = expression()
        consume("RIGHT_PAREN" to "Expect ')' after if condition.")
        val thenBranch = statement()
        val elseBranch: Stmt? = if (match("ELSE")) statement() else null
        return Stmt.If(condition, thenBranch, elseBranch)
    }

    private fun varDeclaration(): Stmt {
        val name = consume("IDENTIFIER" to "Expect variable name.", advance = true)
        val initializer: Expr = if (match("EQUAL")) expression() else Literal(null)
        consume("SEMICOLON" to "Expect ';' after variable declaration.")
        return Stmt.Let(name, initializer)
    }

    private fun expression(): Expr = assignment()

    private fun assignment(): Expr {
        val expr: Expr = or()
        if (match("EQUAL")) {
            when (expr) {
                is Variable -> return Assign(expr.name, assignment())
                is Get -> return Expr.Set(expr.`object`, expr.name, assignment())
                else -> error(previous(), "Invalid assignment target")
            }
        }
        return expr
    }

    private fun or(): Expr {
        var expr: Expr = and()
        while (match("OR")) expr = Binary(expr, previous(), and())
        return expr
    }

    private fun and(): Expr {
        var expr: Expr = equality()
        while (match("AND")) expr = Binary(expr, previous(), equality())
        return expr
    }

    private fun statement(): Stmt {
        return when (peek().type) {
            "WHILE" -> whileStatement()
            "FOR" -> forStatement()
            "IF" -> ifStatement()
            "LEFT_BRACE" -> Stmt.Block(block(true))
            "RETURN" -> returnStatement()
            else -> {
                expressionStatement()
            }
        }
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume("SEMICOLON" to "Expect ';' after expression.")
        return Stmt.Expression(expr)
    }

    private fun equality(): Expr {
        var expr: Expr = comparison()
        while (match("BANG_EQUAL", "EQUAL_EQUAL")) expr = Binary(expr, previous(), comparison())
        return expr
    }

    private fun addition(): Expr {
        var expr = multiplication()
        while (match("MINUS", "PLUS")) expr = Binary(expr, previous(), multiplication())
        return expr
    }

    private fun multiplication(): Expr {
        var expr: Expr = unary()
        while (match("SLASH", "STAR")) expr = Binary(expr, previous(), unary())
        return expr
    }

    private fun comparison(): Expr {
        var expr: Expr = addition()
        while (match("GREATER", "GREATER_EQUAL", "LESS", "LESS_EQUAL")) expr = Binary(expr, previous(), addition())
        return expr
    }

    private fun unary(): Expr = if (match("BANG", "MINUS")) Unary(previous(), unary()) else call()

    private fun finishCall(callee: Expr): Expr {
        val arguments: MutableList<Expr> = ArrayList()
        if (!check("RIGHT_PAREN")) {
            do {
                if (arguments.size >= 255) error(peek(), "Cannot have more than 255 arguments.")
                arguments.add(expression())
            } while (match("COMMA"))
        }
        consume("RIGHT_PAREN" to "Expect ')' after arguments.")
        return Call(callee, arguments)
    }

    private fun call(): Expr {
        var expr = primary()
        loop@ while (true) {
            expr = when {
                match("LEFT_PAREN") -> finishCall(expr)
                match("DOT") -> Get(expr, consume("IDENTIFIER" to "Expect property name after '.'."))
                else -> break@loop
            }
        }
        return expr
    }

    private fun primary(): Expr {
        return when {
            match("FALSE", "TRUE") -> Literal(previous().type.toLowerCase().toBoolean())
            match("NIL") -> Literal(null)
            match("NUMBER", "STRING") -> Literal(previous().literal)
            match("LEFT_PAREN") -> expression().let {
                consume("RIGHT_PAREN" to "Expect ')' after expression.")
                return Grouping(it)
            }
            match("IDENTIFIER") -> Variable(previous())
            match("THIS") -> This(previous())
            match("SUPER") -> {
                val keyword = previous()
                consume("DOT" to "Expect '.' after 'super'.")
                val method = consume("IDENTIFIER" to "Expect superclass method name.")
                return Super(keyword, method)
            }
            else -> throw error(peek(), "Expect expression.")
        }
    }

    private fun advance(): Token? {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if (previous().type === "SEMICOLON") return
            when (peek().type) {
                "CLASS", "FUN", "LET", "FOR", "IF", "WHILE", "RETURN" -> return
            }
            advance()
        }
    }

    private fun match(vararg types: String): Boolean = types.find { peek().type == it }.let {
        if (it != null) {
            advance()
            return true
        } else false
    }

    private fun error(token: Token, message: String): Throwable =
        PartS.error(token, message).let { return ParseError() }

    private fun consume(vararg consumers: Pair<String, String>, advance: Boolean = false): Token? {
        if (advance) advance()
        for (consumer in consumers) {
            if (check(consumer.first)) advance() else throw error(peek(), consumer.second)
        }
        return previous()
    }


    private fun check(type: String): Boolean = if (isAtEnd()) false else peek().type == type
    private fun isAtEnd(): Boolean = peek().type === "EOF"
    private fun peek(): Token = tokens[current]
    private fun previous(): Token = tokens[current - 1]
}