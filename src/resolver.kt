import Expr.*
import Stmt.While
import java.util.*
import kotlin.collections.HashMap

internal class Resolver(private val interpreter: Interpreter) : Visitor<Unit>, Stmt.Visitor<Unit> {
    private val scopes =
        Stack<MutableMap<String, Boolean?>>()
    private var currentFunction = FunctionType.NONE

    private enum class FunctionType {
        NONE, FUNCTION, INITIALIZER, METHOD
    }

    private enum class ClassType {
        NONE, CLASS, SUBCLASS
    }

    private var currentClass = ClassType.NONE
    fun resolve(statements: List<Stmt>) {
        for (statement in statements) {
            resolve(statement)
        }
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        beginScope()
        resolve(stmt.statements)
        endScope()
    }

    override fun visitClassStmt(stmt: Stmt.Class) {
        val enclosingClass = currentClass
        currentClass = ClassType.CLASS
        declare(stmt.name)
        define(stmt.name)
        if (stmt.superclass != null && stmt.name.lexeme == stmt.superclass.name.lexeme) {
            PartS.error(
                stmt.superclass.name,
                "A class cannot inherit from itself."
            )
        }
        if (stmt.superclass != null) {
            currentClass = ClassType.SUBCLASS
            resolve(stmt.superclass)
        }
        if (stmt.superclass != null) {
            beginScope()
            scopes.peek()["super"] = true
        }
        beginScope()
        scopes.peek()["this"] = true
        for (method in stmt.methods) {
            var declaration = FunctionType.METHOD
            if (method.name.lexeme == "init") {
                declaration = FunctionType.INITIALIZER
            }
            resolveFunction(method, declaration) // [local]
        }
        endScope()
        if (stmt.superclass != null) endScope()
        currentClass = enclosingClass
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        resolve(stmt.expression)
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        declare(stmt.name)
        define(stmt.name)
        resolveFunction(stmt, FunctionType.FUNCTION)
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        resolve(stmt.condition)
        resolve(stmt.thenBranch)
        if (stmt.elseBranch != null) resolve(stmt.elseBranch)
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        if (currentFunction == FunctionType.NONE) {
            PartS.error(stmt.keyword, "Cannot return from top-level code.")
        }
        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                PartS.error(
                    stmt.keyword,
                    "Cannot return a value from an initializer."
                )
            }
            resolve(stmt.value)
        }
    }

    override fun visitLetStmt(stmt: Stmt.Let) {
        declare(stmt.name)
        if (stmt.initializer != null) {
            resolve(stmt.initializer)
        }
        define(stmt.name)
        
    }

    override fun visitWhileStmt(stmt: While) {
        resolve(stmt.condition)
        resolve(stmt.body)
        
    }

    override fun visitAssignExpr(expr: Assign) {
        resolve(expr.value)
        resolveLocal(expr, expr.name)
        
    }

    override fun visitBinaryExpr(expr: Binary) {
        resolve(expr.left)
        resolve(expr.right)
        
    }

    override fun visitCallExpr(expr: Call) {
        resolve(expr.callee)
        for (argument in expr.arguments) {
            resolve(argument)
        }
        
    }

    override fun visitGetExpr(expr: Get) {
        resolve(expr.`object`)
        
    }

    override fun visitGroupingExpr(expr: Grouping) {
        resolve(expr.expression)
        
    }

    override fun visitLiteralExpr(expr: Literal) {
        
    }

    override fun visitSetExpr(expr: Expr.Set) {
        resolve(expr.value)
        resolve(expr.`object`)
        
    }

    override fun visitSuperExpr(expr: Super) {
        if (currentClass == ClassType.NONE) {
            PartS.error(
                expr.keyword,
                "Cannot use 'super' outside of a class."
            )
        } else if (currentClass != ClassType.SUBCLASS) {
            PartS.error(
                expr.keyword,
                "Cannot use 'super' in a class with no superclass."
            )
        }
        resolveLocal(expr, expr.keyword)
        
    }

    override fun visitThisExpr(expr: This) {
        if (currentClass == ClassType.NONE) {
            PartS.error(
                expr.keyword,
                "Cannot use 'this' outside of a class."
            )
            
        }
        resolveLocal(expr, expr.keyword)
        
    }

    override fun visitUnaryExpr(expr: Unary) {
        resolve(expr.right)
        
    }

    override fun visitVariableExpr(expr: Variable) {
        if (!scopes.isEmpty() &&
            scopes.peek()[expr.name.lexeme] === java.lang.Boolean.FALSE
        ) {
            PartS.error(
                expr.name,
                "Cannot read local variable in its own initializer."
            )
        }
        resolveLocal(expr, expr.name)
        
    }

    private fun resolve(stmt: Stmt) {
        stmt.accept<Unit>(this)
    }

    private fun resolve(expr: Expr) {
        expr.accept<Unit>(this)
    }

    private fun resolveFunction(
        function: Stmt.Function, type: FunctionType
    ) {
        val enclosingFunction = currentFunction
        currentFunction = type
        beginScope()
        for (param in function.params) {
            declare(param)
            define(param)
        }
        resolve(function.body)
        endScope()
        currentFunction = enclosingFunction
    }

    private fun beginScope() {
        scopes.push(HashMap())
    }

    private fun endScope() {
        scopes.pop()
    }

    private fun declare(name: Token) {
        if (scopes.isEmpty()) return
        val scope = scopes.peek()
        if (scope.containsKey(name.lexeme)) {
            PartS.error(
                name,
                "Variable with this name already declared in this scope."
            )
        }
        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) return
        scopes.peek()[name.lexeme] = true
    }

    private fun resolveLocal(expr: Expr, name: Token) {
        for (i in scopes.indices.reversed()) {
            if (scopes[i].containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size - 1 - i)
                return
            }
        }

        // Not found. Assume it is global.
    }
}