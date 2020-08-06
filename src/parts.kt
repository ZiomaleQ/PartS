import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess


object PartS {
    private var hadError = false
    private var hadRuntimeError = false
    private val interpreter = Interpreter()

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        when {
            args.size > 1 -> {
                println("Usage: parts [script]")
                exitProcess(64)
            }
            args.size == 1 -> runFile(args[0])
            else -> runPrompt()
        }
    }

    @Throws(IOException::class)
    private fun runFile(path: String) {
        val bytes = Files.readAllBytes(Paths.get(path))
        run(String(bytes, Charset.defaultCharset()))
        if (hadError) exitProcess(65)
        if (hadRuntimeError) exitProcess(70)
    }

    @Throws(IOException::class)
    private fun runPrompt() {
        val input = InputStreamReader(System.`in`)
        val reader = BufferedReader(input)
        while (true) {
            print("> ")
            val line = reader.readLine() ?: break
            run(line)
            hadError = false
        }
    }

    private fun run(source: String) {
        val scanner = Scanner(source)
        val tokens: List<Token> = scanner.scanTokens()

        val parser = Parser(tokens)
        val statements = parser.parse()

        // Stop if there was a syntax error.
        if (hadError) return

        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        // Stop if there was a resolution error.
        if (hadError) return

        interpreter.interpret(statements)
    }

    fun error(line: Int, message: String) {
        report(line, "", message)
    }

    private fun report(line: Int, where: String, message: String) {
        System.err.println(
            "[line $line] Error$where: $message"
        )
        hadError = true
    }

    fun error(token: Token, message: String?) {
        if (token.type === "EOF") {
            report(token.line, " at end", message!!)
        } else {
            report(token.line, " at '" + token.lexeme + "'", message!!)
        }
    }

    fun runtimeError(error: RuntimeError) {
        System.err.println("${error.message} [line ${error.token.line}".trimIndent())
        hadRuntimeError = true
    }
}