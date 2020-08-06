import java.util.*

class Token(val type: String, val lexeme: String, val literal: Any?, val line: Int) {
    override fun toString(): String {
        return "$type $lexeme $literal"
    }
}

class RuntimeError(val token: Token, message: String?) : RuntimeException(message)
class Return(val value: Any?) : RuntimeException(null, null, false, false)

class Environment {
    private val values: MutableMap<String, Any?> = mutableMapOf()
    var enclosing: Environment? = null

    constructor() {
        enclosing = null
    }

    constructor(enclosing: Environment? = null) {
        this.enclosing = enclosing
    }

    fun define(name: String, value: Any?) = run { values[name] = value }

    operator fun get(name: Token): Any? = when {
        values.containsKey(name.lexeme) -> values[name.lexeme]
        enclosing != null -> enclosing!![name]
        else -> throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
    }


    fun assign(name: Token, value: Any?) {
        return when {
            values.containsKey(name.lexeme) -> values[name.lexeme] = value
            enclosing != null -> enclosing!!.assign(name, value)
            else -> throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
        }
    }

    fun getAt(distance: Int, name: String?): Any? = ancestor(distance)?.values?.get(name)


    private fun ancestor(distance: Int): Environment? {
        var environment: Environment? = this
        for (i in 0 until distance) environment = environment!!.enclosing
        return environment
    }

    fun assignAt(distance: Int, name: Token, value: Any?) {
        ancestor(distance)!!.values[name.lexeme] = value
    }
}

internal interface PartSCallable {
    fun arity(): Int
    fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
}

internal class PartSFunction(
    private val declaration: Stmt.Function,
    private val closure: Environment,
    private val isInitializer: Boolean = false
) : PartSCallable {

    override fun arity(): Int = declaration.params.size
    override fun toString(): String = "<fn ${declaration.name.lexeme}>"
    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        for (i in declaration.params.indices) environment.define(declaration.params[i].lexeme, arguments[i]!!)
        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (returnValue: Return) {
            return if (isInitializer) closure.getAt(0, "this") else returnValue.value
        }
        return if (isInitializer) closure.getAt(0, "this") else null
    }

    fun bind(instance: PartSInstance?): PartSFunction = Environment(closure).let {
        it.define("this", instance)
        return PartSFunction(declaration, it, isInitializer)
    }
}

internal class PartSClass(
    val name: String,
    private val superclass: PartSClass?,
    private val methods: Map<String, PartSFunction>
) :
    PartSCallable {
    override fun toString(): String = name

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? = PartSInstance(this).let {
        findMethod("init")?.bind(it)?.call(interpreter, arguments)
        return it
    }

    override fun arity(): Int = findMethod("init").let { it?.arity() ?: 0 }

    fun findMethod(name: String): PartSFunction? {
        return when {
            methods.containsKey(name) -> methods[name]
            superclass != null -> superclass.findMethod(name)
            else -> null
        }
    }
}

internal class PartSInstance(private val klass: PartSClass) {
    private val fields: MutableMap<String, Any> = HashMap()
    override fun toString(): String {
        return "${klass.name} instance"
    }

    operator fun get(name: Token): Any? {
        if (fields.containsKey(name.lexeme)) return fields[name.lexeme]
        klass.findMethod(name.lexeme)?.let { return it.bind(this) }

        throw RuntimeError(name, "Undefined property '${name.lexeme}'.")
    }

    operator fun set(name: Token, value: Any?) = run { fields[name.lexeme] = value!! }
}