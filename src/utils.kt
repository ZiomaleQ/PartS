import java.util.*

class Token(val type: String, val lexeme: String, val literal: Any?, val line: Int) {
    override fun toString(): String = "$type $lexeme $literal"
}

class RuntimeError(val token: Token, message: String?) : RuntimeException(message)
class Return(val value: Any?) : RuntimeException(null, null, false, false)
class Environment(var enclosing: Environment? = null) {
    private val values: MutableMap<String, Any?> = mutableMapOf()

    fun define(name: String, value: Any?): Environment {
        values[name] = value
        return this
    }

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


    private fun ancestor(distance: Int): Environment? =
        if (distance <= 0) this.enclosing else this.ancestor(distance - 1)

    fun assignAt(distance: Int, name: Token, value: Any?) {
        ancestor(distance)!!.values[name.lexeme] = value
    }
}

interface PartSCallable {
    fun arity(): Int
    fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
}

class PartSFunction(
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

    fun bind(instance: PartSInstance?): PartSFunction =
        PartSFunction(declaration, Environment(closure).define("this", instance), isInitializer)
}

class PartSClass(
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

    fun findMethod(name: String): PartSFunction? = methods[name] ?: superclass?.findMethod(name)
}

class PartSInstance(private val `class`: PartSClass) {
    private val fields: MutableMap<String, Any> = HashMap()
    override fun toString(): String = "${`class`.name} instance"

    operator fun get(name: Token): Any? {
        if (name.lexeme in fields) return fields[name.lexeme]
        `class`.findMethod(name.lexeme)?.let { return it.bind(this) }

        throw RuntimeError(name, "Undefined property '${name.lexeme}'.")
    }

    operator fun set(name: Token, value: Any?) = run { fields[name.lexeme] = value!! }
}
