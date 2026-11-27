package com.astracode.connectors

/**
 * A bounded matcher for the common .gitignore rules needed by workspace
 * context discovery. Rules are applied from parent directories to children;
 * later matching rules override earlier ones. Negated patterns re-include paths.
 */
data class AiGitIgnoreFile(
    val directory: String,
    val content: String,
)

class AiGitIgnoreMatcher(ignoreFiles: List<AiGitIgnoreFile>) {
    private data class Rule(
        val baseDirectory: String,
        val pattern: Regex,
        val negated: Boolean,
        val directoryOnly: Boolean,
    )

    private val rules: List<Rule> = ignoreFiles
        .sortedBy { it.directory.count { character -> character == '/' } }
        .flatMap { file ->
            parseRules(file.directory, file.content)
        }

    fun isIgnored(path: String, isDirectory: Boolean = false): Boolean {
        if (
            path.isBlank() || path.startsWith("/") || path.contains('\\') ||
            path.split('/').any { it.isBlank() || it == "." || it == ".." }
        ) return true
        val ancestors = mutableListOf<Pair<String, Boolean>>()
        var candidate = path.trimEnd('/')
        ancestors += candidate to isDirectory
        while (candidate.contains('/')) {
            candidate = candidate.substringBeforeLast('/')
            ancestors += candidate to true
        }

        var ignored = false
        rules.forEach { rule ->
            val matched = ancestors.any { (candidatePath, candidateIsDirectory) ->
                (!rule.directoryOnly || candidateIsDirectory) &&
                    rule.matches(candidatePath)
            }
            if (matched) ignored = !rule.negated
        }
        return ignored
    }

    private fun parseRules(baseDirectory: String, text: String): List<Rule> {
        val base = baseDirectory.trim('/').trim()
        return text.lineSequence()
            .take(MAX_RULE_LINES)
            .mapNotNull { rawLine ->
                var value = rawLine.trim()
                if (value.isBlank()) return@mapNotNull null
                if (value.startsWith("\\#") || value.startsWith("\\!")) {
                    value = value.substring(1)
                } else if (value.startsWith("#")) {
                    return@mapNotNull null
                }
                val negated = value.startsWith("!")
                if (negated) value = value.substring(1)
                if (value.isBlank()) return@mapNotNull null
                val directoryOnly = value.endsWith("/")
                value = value.trimEnd('/')
                if (value.isBlank()) return@mapNotNull null
                val anchored = value.startsWith("/")
                if (anchored) value = value.removePrefix("/")
                val hasSlash = value.contains('/')
                val glob = globToRegex(value)
                val regexText = if (hasSlash || anchored) {
                    "^" + glob + (if (directoryOnly) "(?:/.*)?" else "")
                } else {
                    "(?:^|.*/)" + glob + (if (directoryOnly) "(?:/.*)?" else "")
                } + "$"
                Rule(
                    baseDirectory = base,
                    pattern = Regex(regexText),
                    negated = negated,
                    directoryOnly = directoryOnly,
                )
            }
            .toList()
    }

    private fun Rule.matches(path: String): Boolean {
        val relative = when {
            baseDirectory.isBlank() -> path
            path == baseDirectory -> ""
            path.startsWith(baseDirectory + "/") -> path.removePrefix(baseDirectory + "/")
            else -> return false
        }
        return pattern.matches(relative)
    }

    private fun globToRegex(glob: String): String {
        val output = StringBuilder()
        var index = 0
        while (index < glob.length) {
            when (val character = glob[index]) {
                '*' -> {
                    if (index + 1 < glob.length && glob[index + 1] == '*') {
                        if (index + 2 < glob.length && glob[index + 2] == '/') {
                            output.append("(?:.*/)?")
                            index += 2
                        } else {
                            while (index + 1 < glob.length && glob[index + 1] == '*') index++
                            output.append(".*")
                        }
                    } else {
                        output.append("[^/]*")
                    }
                }
                '?' -> output.append("[^/]")
                '[' -> {
                    val closing = glob.indexOf(']', index + 1)
                    if (closing > index + 1) {
                        output.append(glob.substring(index, closing + 1))
                        index = closing
                    } else {
                        output.append("\\[")
                    }
                }
                else -> {
                    if (character in REGEX_SPECIAL_CHARACTERS) output.append('\\')
                    output.append(character)
                }
            }
            index++
        }
        return output.toString()
    }

    private companion object {
        const val MAX_RULE_LINES = 2_000
        val REGEX_SPECIAL_CHARACTERS = setOf('.', '+', '(', ')', '{', '}', '^', '$', '|', '\\')
    }
}
