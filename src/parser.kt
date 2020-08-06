import Expr.*
import Stmt.Return
import Stmt.While
import java.util.*


class Parser(private val tokens: List<Token>) {
    private class ParseError : RuntimeException()

    private var current = 0

    fun parse(): List<Stmt> {
        val statements: MutableList<Stmt> = ArrayList()
        while (!isAtEnd()) {
            declaration().let { if (it != null) statements.add(it); }
        }
        return statements
    }

    private fun declaration(): Stmt? {
        return try {
            return when (peek().type) {
                "CLASS" -> {
                    advance()
                    classDeclaration()
                }
                "FUN" -> {
                    advance()
                    function("function")
                }
                "LET" -> {
                    advance()
                    varDeclaration()
                }
                else -> statement()
            }
        } catch (error: ParseError) {
            synchronize()
            null
        }
    }

    private fun classDeclaration(): Stmt {
        val name = consume("IDENTIFIER", "Expect class name.")
        val superclass: Variable? = if (match("LESS")) {
            consume("IDENTIFIER", "Expect superclass name.")
            Variable(previous())
        } else null
        consume("LEFT_BRACE", "Expect '{' before class body.")
        val methods: MutableList<Stmt.Function?> = ArrayList()
        while (!check("RIGHT_BRACE") && !isAtEnd()) {
            methods.add(function("method"))
        }
        consume("RIGHT_BRACE", "Expect '}' after class body.")
        return Stmt.Class(name, superclass, methods)
    }

    private fun function(kind: String): Stmt.Function? {
        val name = consume("IDENTIFIER", "Expect $kind name.")

        consume("LEFT_PAREN", "Expect '(' after $kind name.")
        val parameters: MutableList<Token?> = ArrayList()
        if (!check("RIGHT_PAREN")) {
            do {
                if (parameters.size >= 255) {
                    error(peek(), "Cannot have more than 255 parameters.")
                }
                parameters.add(consume("IDENTIFIER", "Expect parameter name."))
            } while (match("COMMA"))
        }
        consume("RIGHT_PAREN", "Expect ')' after parameters.")

        consume("LEFT_BRACE", "Expect '{' before $kind body.")
        val body = block()
        return Stmt.Function(name, parameters, body)
    }

    private fun forStatement(): Stmt {
        consume("LEFT_PAREN", "Expect '(' after 'for'.")

        val initializer: Stmt? = when {
            match("SEMICOLON") -> null
            match("LET") -> varDeclaration()
            else -> expressionStatement()
        }

        val condition: Expr? = if (!check("SEMICOLON")) expression() else Literal(true)
        consume("SEMICOLON", "Expect ';' after loop condition.")

        val increment: Expr? = if (!check("RIGHT_PAREN")) expression() else null
        consume("RIGHT_PAREN", "Expect ')' after for clauses.")

        var body = if (increment == null) statement() else Stmt.Block(listOf(statement(), Stmt.Expression(increment)))
        body = While(condition, body, false)

        if (initializer != null) {
            body = Stmt.Block(listOf(initializer, body))
        }
        return body
    }

    private fun returnStatement(): Stmt {
        val keyword = previous()
        val value: Expr? = if (!check("SEMICOLON")) expression() else null
        consume("SEMICOLON", "Expect ';' after return value." + previous())
        return Return(keyword, value)
    }


    private fun whileStatement(): Stmt {
        consume("LEFT_PAREN", "Expect '(' after 'while'.")
        val condition = expression()
        consume("RIGHT_PAREN", "Expect ')' after condition.")
        val body = statement()
        return While(condition, body, false)
    }

    private fun block(): List<Stmt?> {
        val statements: MutableList<Stmt?> = ArrayList()
        while (!check("RIGHT_BRACE") && !isAtEnd()) {
            statements.add(declaration())
        }
        consume("RIGHT_BRACE", "Expect '}' after block.")
        return statements
    }

    private fun ifStatement(): Stmt {
        consume("LEFT_PAREN", "Expect '(' after 'if'.")
        val condition = expression()
        consume("RIGHT_PAREN", "Expect ')' after if condition.")
        val thenBranch = statement()
        var elseBranch: Stmt? = null
        if (match("ELSE")) {
            elseBranch = statement()
        }
        return Stmt.If(condition, thenBranch, elseBranch)
    }

    private fun varDeclaration(): Stmt {
        val name = consume("IDENTIFIER", "Expect variable name.")
        val initializer: Expr = if (match("EQUAL")) expression() else Literal(null)
        consume("SEMICOLON", "Expect ';' after variable declaration.")
        return Stmt.Let(name, initializer)
    }

    private fun expression(): Expr {
        return assignment()
    }

    private fun assignment(): Expr {
        val expr: Expr = or()
        if (match("EQUAL")) {
            val equals = previous()
            val value = assignment()
            when (expr) {
                is Variable -> return Assign(expr.name, value)
                is Get -> return Expr.Set(expr.`object`, expr.name, value)
                else -> error(equals, "Invalid assignment target")
            }
        }
        return expr
    }

    private fun or(): Expr {
        var expr: Expr = and()
        while (match("OR")) {
            val operator = previous()
            val right: Expr = and()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun and(): Expr {
        var expr: Expr = equality()
        while (match("AND")) {
            val operator = previous()
            val right = equality()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun statement(): Stmt {
        return when (peek().type) {
            "WHILE" -> {
                advance()
                whileStatement()
            }
            "FOR" -> {
                advance()
                forStatement()
            }
            "IF" -> {
                advance()
                ifStatement()
            }
            "LEFT_BRACE" -> {
                advance()
                Stmt.Block(block())
            }
            "RETURN" -> {
                advance()
                returnStatement()
            }
            else -> {
                expressionStatement()
            }
        }
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume("SEMICOLON", "Expect ';' after expression.")
        return Stmt.Expression(expr)
    }

    private fun equality(): Expr {
        var expr: Expr = comparison()
        while (match("BANG_EQUAL", "EQUAL_EQUAL")) {
            val operator: Token = previous()
            val right: Expr = comparison()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun addition(): Expr {
        var expr = multiplication()
        while (match("MINUS", "PLUS")) {
            val operator = previous()
            val right = multiplication()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun multiplication(): Expr {
        var expr: Expr = unary()
        while (match("SLASH", "STAR")) {
            val operator = previous()
            val right: Expr = unary()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun comparison(): Expr {
        var expr: Expr = addition()
        while (match("GREATER", "GREATER_EQUAL", "LESS", "LESS_EQUAL")) {
            val operator = previous()
            val right: Expr = addition()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun unary(): Expr {
        if (match("BANG", "MINUS")) {
            val operator = previous()
            val right = unary()
            return Unary(operator, right)
        }
        return call()
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments: MutableList<Expr> = ArrayList()
        if (!check("RIGHT_PAREN")) {
            do {
                if (arguments.size >= 255) error(peek(), "Cannot have more than 255 arguments.")
                arguments.add(expression())
            } while (match("COMMA"))
        }
        consume("RIGHT_PAREN", "Expect ')' after arguments.")
        return Call(callee, arguments)
    }

    private fun call(): Expr {
        var expr = primary()
        loop@ while (true) {
            expr = when {
                match("LEFT_PAREN") -> finishCall(expr)
                match("DOT") -> Get(expr, consume("IDENTIFIER", "Expect property name after '.'."))
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
                consume("RIGHT_PAREN", "Expect ')' after expression.");
                return Grouping(it)
            }
            match("IDENTIFIER") -> Variable(previous())
            match("THIS") -> This(previous())
            match("SUPER") -> {
                val keyword = previous()
                consume("DOT", "Expect '.' after 'super'.")
                val method = consume("IDENTIFIER", "Expect superclass method name.")
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

    private fun consume(type: String, message: String): Token? =
        if (check(type)) advance() else throw error(peek(), message)

    private fun check(type: String): Boolean = if (isAtEnd()) false else peek().type == type
    private fun isAtEnd(): Boolean = peek().type === "EOF"
    private fun peek(): Token = tokens[current]
    private fun previous(): Token = tokens[current - 1]
}