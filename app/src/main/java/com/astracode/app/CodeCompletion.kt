package com.astracode.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.util.Locale

private const val COMPLETION_SCAN_RADIUS = 32 * 1024
private const val DEFAULT_COMPLETION_LIMIT = 8
private const val MAX_COMPLETION_LABEL_LENGTH = 256
private val COMPLETION_IDENTIFIER = Regex("""[\p{L}\p{Nl}_$][\p{L}\p{Nl}\p{N}\p{M}_$]*""")
private val HYPHENATED_COMPLETION_IDENTIFIER =
    Regex("""(?:--)?[\p{L}\p{Nl}_$][\p{L}\p{Nl}\p{N}\p{M}_$-]*""")

private fun completionWords(value: String): Set<String> =
    value.split(Regex("\\s+")).filter { it.isNotBlank() }.toCollection(LinkedHashSet())

private val PROFILE_COMPLETIONS: Map<SyntaxProfile, Set<String>> = mapOf(
    SyntaxProfile.C_LIKE to completionWords(
        """if else for while do switch case default break continue return try catch finally throw throws
        class interface enum extends implements public private protected internal static final abstract
        virtual override async await yield import export from as const let var function fun val when object
        companion package namespace using include define typedef sizeof alignof auto extern inline volatile
        struct union template typename operator this super self true false null nil None True False undefined
        NaN Infinity bool byte char short int long float double decimal string String number dynamic void
        int8 int16 int32 int64 uint8 uint16 uint32 uint64 float32 float64 main lambda fn pub crate mod use
        mut impl trait where match loop move ref type data sealed lateinit lazy suspend reified actual expect
        new delete typeof instanceof console log print println printf assert synchronized System Math Arrays
        List Map Set Pair Result Optional Promise Array Object JSON Date Error IOException StringBuilder"""
    ),
    SyntaxProfile.PYTHON to completionWords(
        """False None True and as assert async await break class continue def del elif else except finally
        for from global if import in is lambda nonlocal not or pass raise return try while with yield self cls
        print len range enumerate zip map filter sorted reversed sum min max abs all any isinstance issubclass
        open input str int float bool list tuple dict set frozenset bytes bytearray object type super property
        staticmethod classmethod dataclass Path json re os sys math random pathlib asyncio typing"""
    ),
    SyntaxProfile.SHELL to completionWords(
        """if then else elif fi for while until do done case esac in function select export readonly unset
        source alias unalias cd pwd set shift eval exec exit return break continue test read printf echo trap
        wait command builtin hash type jobs fg bg kill grep sed awk find xargs cat head tail sort uniq cut
        git python node npm gradle java cargo go make cmake curl wget"""
    ),
    SyntaxProfile.SQL to completionWords(
        """select from where join left right inner outer full cross on as and or not null is distinct group by
        order having limit offset fetch first next rows only insert into values update set delete create alter
        drop table view index schema database grant revoke commit rollback transaction begin end case when then
        else primary key foreign references unique constraint default returning union all except intersect exists
        like ilike count sum avg min max coalesce cast current_date current_timestamp varchar integer boolean"""
    ),
    SyntaxProfile.HTML to completionWords(
        """html head body title meta link script style div span p a img button input form label select option
        textarea table thead tbody tr th td ul ol li main nav header footer section article aside h1 h2 h3
        class id href src alt type name value placeholder disabled checked required onclick aria-label"""
    ),
    SyntaxProfile.CSS to completionWords(
        """color background display position absolute relative fixed sticky flex grid block inline none margin
        padding border radius width height min-width max-width min-height max-height font font-size font-weight
        line-height text-align text-decoration justify-content align-items gap overflow transform transition
        animation opacity box-shadow outline z-index cursor media keyframes hover focus root important"""
    ),
    SyntaxProfile.JSON to completionWords("""true false null"""),
    SyntaxProfile.JSON_WITH_COMMENTS to completionWords("""true false null undefined"""),
    SyntaxProfile.YAML to completionWords("""true false null yes no on off name version services image ports volumes environment build command deploy replicas"""),
    SyntaxProfile.TOML to completionWords("""true false name version dependencies package description authors license scripts devDependencies"""),
    SyntaxProfile.CONFIG to completionWords("""true false yes no on off name value path file enabled disabled default include exclude export host port user group"""),
    SyntaxProfile.LUA to completionWords(
        """and break do else elseif end false for function if in local nil not or repeat return then true until while
        pairs ipairs next print require module type tostring tonumber select assert error pcall xpcall math string table io os coroutine"""
    ),
    SyntaxProfile.RUBY to completionWords(
        """alias and begin break case class def defined do else elsif end ensure false for if in module next nil
        not or redo rescue retry return self super then true undef unless until when while yield puts print require
        attr_reader attr_writer attr_accessor each map select reject reduce inject puts gets File Dir Array Hash String"""
    ),
    SyntaxProfile.HASKELL to completionWords(
        """case class data default deriving do else foreign if import in infix infixl infixr instance let module newtype
        of then type where otherwise fst snd curry uncurry id const map filter foldr foldl head tail length null"""
    ),
    SyntaxProfile.R to completionWords(
        """if else repeat while function for in next break TRUE FALSE NULL NA NaN Inf library require source print paste
        data.frame list vector matrix tibble mutate filter select arrange group_by summarise ggplot read.csv write.csv"""
    ),
    SyntaxProfile.MARKDOWN to emptySet(),
    SyntaxProfile.PLAIN to emptySet(),
)

private fun completionKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

private fun normalizeCompletionLabel(value: String): String? {
    val label = value.trim()
    if (label.isEmpty() || label.length > MAX_COMPLETION_LABEL_LENGTH) return null
    if (label.any { Character.isISOControl(it) }) return null
    return label
}

private fun supportsHyphenatedIdentifiers(path: String?): Boolean {
    val profile = path?.let { detectSyntaxLanguage(it)?.profile }
    return profile == SyntaxProfile.CSS || profile == SyntaxProfile.HTML
}

private fun isCompletionIdentifierCodePoint(codePoint: Int, allowHyphen: Boolean): Boolean =
    codePoint == '_'.code || codePoint == '$'.code ||
        Character.isUnicodeIdentifierPart(codePoint) ||
        (allowHyphen && codePoint == '-'.code)

internal fun codeCompletionPrefixRange(
    text: String,
    caretOffset: Int,
    path: String? = null,
): TextRange? {
    val caret = caretOffset.coerceIn(0, text.length)
    val allowHyphen = supportsHyphenatedIdentifiers(path)
    if (caret == 0 || splitsSurrogatePair(text, caret)) return null

    val lastCodePoint = text.codePointBefore(caret)
    if (!isCompletionIdentifierCodePoint(lastCodePoint, allowHyphen)) return null

    var start = caret - Character.charCount(lastCodePoint)
    while (start > 0) {
        if (splitsSurrogatePair(text, start)) break
        val codePoint = text.codePointBefore(start)
        if (!isCompletionIdentifierCodePoint(codePoint, allowHyphen)) break
        start -= Character.charCount(codePoint)
    }
    return TextRange(start, caret)
}

/** Returns the complete identifier surrounding a caret inside an identifier. */
internal fun codeCompletionIdentifierRange(
    text: String,
    caretOffset: Int,
    path: String? = null,
): TextRange? {
    val prefixRange = codeCompletionPrefixRange(text, caretOffset, path) ?: return null
    val allowHyphen = supportsHyphenatedIdentifiers(path)
    var end = prefixRange.end
    while (end < text.length) {
        val codePoint = text.codePointAt(end)
        if (!isCompletionIdentifierCodePoint(codePoint, allowHyphen)) break
        end += Character.charCount(codePoint)
    }
    return TextRange(prefixRange.start, end)
}

private data class LocalCompletionCandidate(
    val label: String,
    val key: String,
    val distance: Int,
    val exactCase: Boolean,
    val sourceOrder: Int,
)

/** Returns offline, language-aware suggestions for the identifier immediately before the caret. */
internal fun codeCompletionSuggestions(
    sourceText: String,
    path: String,
    caretOffset: Int,
    limit: Int = DEFAULT_COMPLETION_LIMIT,
): List<String> {
    if (limit <= 0) return emptyList()
    val language = detectSyntaxLanguage(path) ?: return emptyList()
    if (language.profile == SyntaxProfile.PLAIN || language.profile == SyntaxProfile.MARKDOWN) {
        return emptyList()
    }
    val prefixRange = codeCompletionPrefixRange(sourceText, caretOffset, path) ?: return emptyList()
    val prefix = sourceText.substring(prefixRange.start, prefixRange.end)
    if (prefix.isEmpty()) return emptyList()

    val caret = caretOffset.coerceIn(0, sourceText.length)
    var scanStart = (caret - COMPLETION_SCAN_RADIUS).coerceAtLeast(0)
    var scanEnd = (caret + COMPLETION_SCAN_RADIUS).coerceAtMost(sourceText.length)
    if (splitsSurrogatePair(sourceText, scanStart)) scanStart--
    if (splitsSurrogatePair(sourceText, scanEnd)) scanEnd++
    val identifierPattern = if (supportsHyphenatedIdentifiers(path)) {
        HYPHENATED_COMPLETION_IDENTIFIER
    } else {
        COMPLETION_IDENTIFIER
    }
    val localOccurrences = identifierPattern.findAll(sourceText.substring(scanStart, scanEnd))
        .mapNotNull { match ->
            val label = normalizeCompletionLabel(match.value) ?: return@mapNotNull null
            if (!label.startsWith(prefix, ignoreCase = true) ||
                label.equals(prefix, ignoreCase = true)
            ) {
                return@mapNotNull null
            }
            LocalCompletionCandidate(
                label = label,
                key = completionKey(label),
                distance = kotlin.math.abs(scanStart + match.range.first - caret),
                exactCase = label.startsWith(prefix),
                sourceOrder = match.range.first,
            )
        }
        .toList()

    val frequency = localOccurrences.groupingBy { it.key }.eachCount()
    val localCandidates = localOccurrences
        .groupBy { it.key }
        .values
        .mapNotNull { occurrences ->
            occurrences.minWithOrNull(
                compareByDescending<LocalCompletionCandidate> { it.exactCase }
                    .thenBy { it.distance }
                    .thenBy { it.sourceOrder },
            )
        }
        .sortedWith(
            compareByDescending<LocalCompletionCandidate> { it.exactCase }
                .thenBy { it.distance }
                .thenByDescending { frequency[it.key] ?: 0 }
                .thenBy { it.sourceOrder },
        )

    val languageWords = PROFILE_COMPLETIONS[language.profile].orEmpty() +
        LANGUAGE_COMPLETIONS[language.name].orEmpty()
    val rankedLanguageWords = languageWords
        .mapNotNull(::normalizeCompletionLabel)
        .filter { it.startsWith(prefix, ignoreCase = true) && !it.equals(prefix, ignoreCase = true) }
        .distinctBy(::completionKey)
        .sortedWith(
            compareByDescending<String> { it.startsWith(prefix) }
                .thenBy { it.lowercase(Locale.ROOT) },
        )

    val result = LinkedHashMap<String, String>()
    fun add(label: String) {
        val key = completionKey(label)
        if (key !in result && result.size < limit.coerceAtMost(16)) result[key] = label
    }
    localCandidates.forEach { add(it.label) }
    rankedLanguageWords.forEach(::add)
    return result.values.toList()
}

/**
 * Combines server and offline completions, prioritizing the language server.
 *
 * Labels are de-duplicated without regard to case because providers can return
 * the same completion with different capitalization.
 */
internal fun mergeCodeCompletionSuggestions(
    languageServerSuggestions: List<String>,
    localSuggestions: List<String>,
    limit: Int = 16,
): List<String> {
    if (limit <= 0) return emptyList()

    val maximum = limit.coerceAtMost(16)
    val seen = HashSet<String>()
    val merged = ArrayList<String>(maximum)

    for (rawSuggestion in languageServerSuggestions + localSuggestions) {
        val suggestion = normalizeCompletionLabel(rawSuggestion) ?: continue
        if (!seen.add(completionKey(suggestion))) continue

        merged += suggestion
        if (merged.size == maximum) break
    }

    return merged
}

/** Applies a suggestion to a prefix range captured when the suggestion row was rendered. */
internal fun applyCodeCompletionAtRange(
    value: TextFieldValue,
    suggestion: String,
    range: TextRange,
    path: String? = null,
): TextFieldValue? {
    val insertion = normalizeCompletionLabel(suggestion) ?: return null
    if (range.start < 0 || range.end > value.text.length || range.start >= range.end) return null
    if (splitsSurrogatePair(value.text, range.start) || splitsSurrogatePair(value.text, range.end)) return null

    val allowHyphen = supportsHyphenatedIdentifiers(path)
    if (range.start > 0) {
        val previousCodePoint = value.text.codePointBefore(range.start)
        if (isCompletionIdentifierCodePoint(previousCodePoint, allowHyphen)) return null
    }

    val prefix = value.text.substring(range.start, range.end)
    var prefixOffset = 0
    while (prefixOffset < prefix.length) {
        val codePoint = prefix.codePointAt(prefixOffset)
        if (!isCompletionIdentifierCodePoint(codePoint, allowHyphen)) return null
        prefixOffset += Character.charCount(codePoint)
    }
    if (!insertion.startsWith(prefix, ignoreCase = true)) return null

    // Replace the whole identifier, including hyphenated CSS properties and HTML attributes.
    val identifierRange = codeCompletionIdentifierRange(value.text, range.end, path)
        ?: TextRange(range.start, range.end)
    if (identifierRange.start != range.start) return null
    val nextText = value.text.replaceRange(identifierRange.start, identifierRange.end, insertion)
    return TextFieldValue(nextText, selection = TextRange(identifierRange.start + insertion.length))
}

@Composable
internal fun CodeCompletionRow(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("editor-completion-row"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        suggestions.forEach { suggestion ->
            TextButton(
                onClick = { onSelect(suggestion) },
                modifier = Modifier.testTag("editor-completion-$suggestion"),
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
