import Expr.*
import Stmt.Return
import Stmt.While
import java.util.*
import java.lang.System.currentTimeMillis


class Interpreter : Visitor<Any?>, Stmt.Visitor<Unit> {
    private val locals: MutableMap<Expr, Int> = HashMap()
    private val globals = Environment()
    private var environment = globals

    init {
        globals.define("clock", object : PartSCallable {
            override fun arity(): Int = 0
            override fun call(interpreter: Interpreter, arguments: List<Any?>) = currentTimeMillis().toDouble() / 1000.0
            override fun toString(): String = "<native fn>"
        })
        globals.define("print",
            object : PartSCallable {
                override fun arity(): Int = 1
                override fun call(interpreter: Interpreter, arguments: List<Any?>) = println(stringify(arguments[0]))
                override fun toString(): String = "<native fn>"
            })
    }

    fun interpret(statements: List<Stmt>) {
        try {
            for (statement in statements) execute(statement)
        } catch (error: RuntimeError) {
            PartS.runtimeError(error)
        }
    }

    fun executeBlock(statements: List<Stmt>, environment: Environment) {
        val previous = this.environment
        try {
            this.environment = environment
            for (statement in statements) execute(statement)
        } finally {
            this.environment = previous
        }
    }

    override fun visitIfStmt(stmt: Stmt.If) = when {
        isTruthy(evaluate(stmt.condition)) -> execute(stmt.thenBranch)
        stmt.elseBranch != null -> execute(stmt.elseBranch)
        else -> null
    }

    override fun visitReturnStmt(stmt: Return) = throw Return(if (stmt.value != null) evaluate(stmt.value) else null)
    private fun execute(stmt: Stmt) = stmt.accept(this)
    override fun visitExpressionStmt(stmt: Stmt.Expression) = evaluate(stmt.expression).let { null }
    override fun visitVariableExpr(expr: Variable) = lookUpVariable(expr.name, expr)
    override fun visitLetStmt(stmt: Stmt.Let) = environment.define(stmt.name.lexeme, evaluate(stmt.initializer))
    override fun visitLiteralExpr(expr: Literal): Any? = expr.value
    override fun visitGroupingExpr(expr: Grouping): Any? = evaluate(expr.expression)
    private fun evaluate(expr: Expr) = expr.accept<Any>(this)
    override fun visitThisExpr(expr: This) = lookUpVariable(expr.keyword, expr)
    override fun visitBlockStmt(stmt: Stmt.Block) = executeBlock(stmt.statements, Environment(environment))
    override fun visitWhileStmt(stmt: While) = run { while (isTruthy(evaluate(stmt.condition))) execute(stmt.body) }

    override fun visitFunctionStmt(stmt: Stmt.Function) =
        environment.define(stmt.name.lexeme, PartSFunction(stmt, environment))

    override fun visitAssignExpr(expr: Assign): Any? {
        val value = evaluate(expr.value)
        locals[expr].let {
            if (it != null) environment.assignAt(it, expr.name, value)
            else globals.assign(expr.name, value)
        }
        return value
    }

    override fun visitUnaryExpr(expr: Unary): Any? {
        val right = evaluate(expr.right)
        return when (expr.operator.type) {
            "MINUS" -> {
                if (right != null) checkNumberOperand(expr.operator, right)
                return -(right as Double)
            }
            "BANG" -> !isTruthy(right)
            else -> null
        }
    }

    override fun visitBinaryExpr(expr: Binary): Any? {
        when (expr.operator.type) {
            "OR", "AND" -> {
                evaluate(expr.left).let {
                    if (expr.operator.type === "OR") if (isTruthy(it)) return it
                    else if (!isTruthy(it)) return it
                }
                return evaluate(expr.right)
            }
        }

        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        when (expr.operator.type) {
            "GREATER", "GREATER_EQUAL", "LESS", "LESS_EQUAL",
            "MINUS", "STAR", "SLASH" -> checkNumberOperands(expr.operator, left!!, right!!)
        }

        return when (expr.operator.type) {
            "GREATER" -> left as Double > right as Double
            "GREATER_EQUAL" -> left as Double >= right as Double
            "LESS" -> (left as Double).compareTo(right as Double) < 0
            "LESS_EQUAL" -> left as Double <= right as Double
            "MINUS" -> left as Double - right as Double
            "PLUS" -> {
                if (left is Double && right is Double) return left + right
                if (left is String && right is String) return "$left$right"
                throw RuntimeError(expr.operator, "Operands must be two numbers or two strings.")
            }
            "SLASH" -> left as Double / right as Double
            "STAR" -> left as Double * right as Double
            "BANG_EQUAL" -> !isEqual(left, right)
            "EQUAL_EQUAL" -> isEqual(left, right)
            else -> null
        }
    }

    override fun visitCallExpr(expr: Call): Any? {
        val arguments: MutableList<Any?> = ArrayList()
        for (argument in expr.arguments) {
            arguments.add(evaluate(argument!!))
        }

        val function: PartSCallable = evaluate(expr.callee).let {
            if (it !is PartSCallable) throw RuntimeError(
                Token("", "", "", 0), "Can only call functions and classes."
            ) else it
        }
        if (arguments.size != function.arity()) {
            throw RuntimeError(
                Token("", "", "", 0),
                "Expected ${function.arity()} arguments but got ${arguments.size}."
            )
        }
        return function.call(this, arguments)
    }

    private fun isTruthy(`object`: Any?): Boolean =
        if (`object` == null) false else (if (`object` is Boolean) `object` else true)

    private fun isEqual(a: Any?, b: Any?): Boolean =
        if (a == null && b == null) true else run { if (a == null) false else a == b }

    private fun stringify(`object`: Any?): String {
        if (`object` == null) return "nil"

        // Print without additional '.0' when number is double value
        if (`object` is Double) return "$`object`".let { if (it.endsWith(".0")) it.substring(0, it.length - 2) else it }
        return `object`.toString()
    }

    private fun checkNumberOperand(operator: Token, operand: Any) = if (operand is Double) null
    else throw RuntimeError(operator, "Operand must be a number.")

    private fun checkNumberOperands(operator: Token, left: Any, right: Any) {
        if (left is Double && right is Double) return
        throw RuntimeError(operator, "Operands must be numbers.")
    }

    fun resolve(expr: Expr, depth: Int) = run { locals[expr] = depth }
    private fun lookUpVariable(name: Token, expr: Expr): Any? =
        locals[expr].let { if (it != null) environment.getAt(it, name.lexeme) else globals[name] }


    override fun visitGetExpr(expr: Get): Any? {
        evaluate(expr.`object`).let { if (it is PartSInstance) return it[expr.name] }
        throw RuntimeError(expr.name, "Only instances have properties.")
    }

    override fun visitSetExpr(expr: Expr.Set): Any? {
        val `object` =
            evaluate(expr.`object`) as? PartSInstance ?: throw RuntimeError(expr.name, "Only instances have fields.")
        evaluate(expr.value).let { `object`[expr.name] = it; return it }
    }

    override fun visitSuperExpr(expr: Super?): Any? {
        return locals[expr as Expr]?.let {
            val superclass: PartSClass? = environment.getAt(it, "super") as PartSClass?
            val `object`: PartSInstance? = environment.getAt(it - 1, "this") as PartSInstance?

            val method: PartSFunction = superclass!!.findMethod(expr.method.lexeme)
                ?: throw RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.")
            method.bind(`object`)
        }
    }

    override fun visitClassStmt(stmt: Stmt.Class) {
        val superclass: Any? = if (stmt.superclass == null) null else {
            evaluate(stmt.superclass).let {
                if (it !is PartSClass) throw RuntimeError(stmt.superclass.name, "Superclass must be a class.")
                else it
            }
        }

        environment.define(stmt.name.lexeme, null)
        if (stmt.superclass != null) {
            environment = Environment(environment)
            environment.define("super", superclass)
        }
        val methods: MutableMap<String, PartSFunction> = HashMap()
        for (method in stmt.methods) methods[method.name.lexeme] =
            PartSFunction(method, environment, method.name.lexeme == "init")

        val klass = PartSClass(stmt.name.lexeme, superclass as PartSClass?, methods)
        if (superclass != null) environment = environment.enclosing!!
        environment.assign(stmt.name, klass)
    }
}