package ir.ilam.inspection.util

/** Iranian national ID: ten digits with the check digit; the same rule as the server's Persons. */
object NationalId {

    fun normalized(input: String): String = PersianNumbers.toLatin(input).filter { it.isDigit() }

    fun isValid(input: String): Boolean {
        val code = normalized(input)
        if (code.length != 10 || code.all { it == code[0] }) return false
        val sum = (0 until 9).sumOf { (code[it] - '0') * (10 - it) }
        val rest = sum % 11
        val check = code[9] - '0'
        return if (rest < 2) check == rest else check == 11 - rest
    }
}
