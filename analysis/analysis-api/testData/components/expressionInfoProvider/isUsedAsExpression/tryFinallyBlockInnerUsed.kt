fun test() {
    val x = try {
        4
    } catch (e: Exception) {
        5
    } finally {
        <expr>9</expr>
    }
}