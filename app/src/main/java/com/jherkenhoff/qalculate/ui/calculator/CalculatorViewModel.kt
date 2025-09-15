package com.jherkenhoff.qalculate.ui.calculator

import com.jherkenhoff.qalculate.data.CurrencyConversionHelper
import com.jherkenhoff.qalculate.data.CurrencyDatabase
import com.jherkenhoff.qalculate.data.CurrencyRateRepository
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.text.input.getTextAfterSelection
import androidx.compose.ui.text.input.getTextBeforeSelection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jherkenhoff.libqalculate.Calculator
import com.jherkenhoff.libqalculate.IntervalDisplay
import com.jherkenhoff.libqalculate.PrintOptions
import com.jherkenhoff.libqalculate.libqalculateConstants.TAG_TYPE_HTML
import com.jherkenhoff.qalculate.data.AutocompleteRepository
import com.jherkenhoff.qalculate.data.CurrencyAutocompleteProvider
import com.jherkenhoff.qalculate.data.CalculationHistoryRepository
import com.jherkenhoff.qalculate.data.PersistentCalculationHistoryRepository
import com.jherkenhoff.qalculate.data.ScreenSettingsRepository
import com.jherkenhoff.qalculate.data.ThemeSettingsRepository
import com.jherkenhoff.qalculate.data.model.CalculationHistoryItem
import com.jherkenhoff.qalculate.domain.CalculateUseCase
import com.jherkenhoff.qalculate.domain.ParseUseCase
import com.jherkenhoff.qalculate.model.AutocompleteItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

data class CalculatorUiState (
    val autocompleteList: List<AutocompleteItem> = emptyList(),
)


@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val calculator: Calculator,
    private val parseUseCase: ParseUseCase,
    private val calculateUseCase: CalculateUseCase,
    private val screenSettingsRepository: ScreenSettingsRepository,
    private val autocompleteRepository: AutocompleteRepository,
    private val themeSettingsRepository: ThemeSettingsRepository,
    @ApplicationContext private val applicationContext: android.content.Context
) : ViewModel() {

    // Currency autocomplete provider
    private val currencyAutocompleteProvider = CurrencyAutocompleteProvider(applicationContext)

    private val calculationHistoryRepository = PersistentCalculationHistoryRepository(applicationContext)

    // Currency DB/repository for rates
    private val currencyDb by lazy { CurrencyDatabase.getInstance(applicationContext) }
    private val currencyRateRepo by lazy { CurrencyRateRepository(currencyDb.currencyRateDao(), applicationContext) }

    val darkThemeFlow = themeSettingsRepository.isDarkTheme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch {
            themeSettingsRepository.saveDarkTheme(isDark)
        }
    }

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    private var autocompleteJob: Job? = null
    private var calculateJob: Job? = null

    private var autocompleteDismissed = false

    val calculationHistory = calculationHistoryRepository
        .observeCalculationHistory()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val altKeyboardOpen = screenSettingsRepository.isAltKeyboardOpen

    var parsedString by mutableStateOf("")
        private set

    var resultString by mutableStateOf("0")
        private set

    var inputTextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    fun dismissAutocomplete() {
        _uiState.update { currentState ->
            currentState.copy(
                autocompleteList = emptyList()
            )
        }

        autocompleteDismissed = true
    }

    fun submitCalculation() {
        viewModelScope.launch {
            calculationHistoryRepository.appendCalculation(
                CalculationHistoryItem(
                    java.time.LocalDateTime.now().toString(),
                    inputTextFieldValue.text,
                    parsedString,
                    resultString
                )
            )
            updateInput(TextFieldValue(""))
        }
    }

    fun toggleAltKeyboard(newState: Boolean) {
        runBlocking {
            screenSettingsRepository.saveAltKeyboardOpen(newState)
        }
    }

    fun updateInput(input: TextFieldValue) {
        inputTextFieldValue = input
        handleAutocomplete()
        recalculate()
    }

    private fun handleAutocomplete() {

        val textBefore = inputTextFieldValue.getTextBeforeSelection(inputTextFieldValue.text.length).toString()

        val pattern = Regex("([a-zA-Z_]+$)")

        val match = pattern.find(textBefore)

        if (match == null) {
            _uiState.update { currentState ->
                currentState.copy(
                    autocompleteList = emptyList()
                )
            }

            autocompleteDismissed = false
            return
        }

        if (autocompleteDismissed) return

        autocompleteJob?.cancel()

        autocompleteJob = viewModelScope.launch {
            delay(100)

            val relevantText = match.value

            // Get normal autocomplete suggestions
            val autocompleteList = autocompleteRepository.getAutocompleteSuggestions(relevantText)

            // Get currency suggestions

            val currencySuggestions = currencyAutocompleteProvider.suggest(relevantText)
                .map { (code, name) ->
                    AutocompleteItem(
                        type = com.jherkenhoff.qalculate.model.AutocompleteType.CURRENCY,
                        name = code,
                        title = name,
                        abbreviations = listOf(code),
                        description = name,
                        typeBeforeCursor = code + " ",
                        typeAfterCursor = ""
                    )
                }

            val mergedList = (autocompleteList + currencySuggestions).distinctBy { it.name }

            _uiState.update { currentState ->
                currentState.copy(
                    autocompleteList = mergedList
                )
            }
        }
    }

    fun acceptAutocomplete(beforeCursorText: String, afterCursorText: String) {

        val textBefore = inputTextFieldValue.getTextBeforeSelection(inputTextFieldValue.text.length).toString()
        val textAfter = inputTextFieldValue.getTextAfterSelection(inputTextFieldValue.text.length).toString()


        val pattern = Regex("([a-zA-Z_]+$)")

        val match = pattern.find(textBefore)

        val textBeforeWithoutRelevant = pattern.split(textBefore).first()

        updateInput(TextFieldValue(
            text = textBeforeWithoutRelevant + beforeCursorText + afterCursorText + textAfter,
            selection = TextRange(textBeforeWithoutRelevant.length + beforeCursorText.length)
        ))
    }

    fun insertText(preCursorText: String, postCursorText: String = "") {
        val maxChars = inputTextFieldValue.text.length
        val textBeforeSelection = inputTextFieldValue.getTextBeforeSelection(maxChars)
        val textAfterSelection = inputTextFieldValue.getTextAfterSelection(maxChars)
        val newText = "$textBeforeSelection$preCursorText$postCursorText$textAfterSelection"
        val newCursorPosition = textBeforeSelection.length + preCursorText.length

        updateInput(TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPosition)
        ))
    }

    fun removeLastChar() {
        val maxChars = inputTextFieldValue.text.length
        val textBeforeSelection = inputTextFieldValue.getTextBeforeSelection(maxChars)
        val textAfterSelection = inputTextFieldValue.getTextAfterSelection(maxChars)
        val selectedText = inputTextFieldValue.getSelectedText()

        var newText: String
        var newCursorPosition: Int

        if (selectedText.isEmpty()) {
            if (textBeforeSelection.isEmpty()) {
                return
            } else {
                newText = "${textBeforeSelection.dropLast(1)}$textAfterSelection"
                newCursorPosition = textBeforeSelection.length - 1
            }
        } else {
            newText = "$textBeforeSelection$textAfterSelection"
            newCursorPosition = textBeforeSelection.length
        }

        updateInput(TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPosition)
        ))
    }

    fun clearAll() {
        updateInput(TextFieldValue(
            text = "",
            selection = TextRange(0)
        ))
    }

    private fun recalculate() {

        calculateJob
        calculateJob?.cancel()

        calculateJob = viewModelScope.launch {
            delay(100)

            val input = inputTextFieldValue.text.trim()
            // Regex: e.g. 1 usd to inr
            val currencyPattern = Regex("(\\d+(?:\\.\\d+)?)\\s+([a-zA-Z]{2,10})\\s+to\\s+([a-zA-Z]{2,10})", RegexOption.IGNORE_CASE)
            val match = currencyPattern.matchEntire(input)
            if (match != null) {
                val amount = match.groupValues[1].toDoubleOrNull()
                val from = match.groupValues[2].lowercase()
                val to = match.groupValues[3].lowercase()
                if (amount != null) {
                    // Get rates JSON (suspend)
                    val ratesJson = currencyRateRepo.getRatesForToday()
                    if (ratesJson != null) {
                        val result = CurrencyConversionHelper.convert(amount, from, to, ratesJson)
                        if (result != null) {
                            parsedString = "$amount $from = $result $to"
                            resultString = result.toString()
                            return@launch
                        } else {
                            parsedString = "Conversion not available"
                            resultString = "-"
                            return@launch
                        }
                    } else {
                        parsedString = "Rates unavailable"
                        resultString = "-"
                        return@launch
                    }
                }
            }

            // Fallback to normal calculation
            parsedString = parseUseCase(inputTextFieldValue.text)

            val calculatedMathStructure = calculateUseCase(inputTextFieldValue.text)

            val resultPo = PrintOptions()
            resultPo.interval_display = IntervalDisplay.INTERVAL_DISPLAY_SIGNIFICANT_DIGITS
            resultPo.use_unicode_signs = 1
            resultString = calculator.print(calculatedMathStructure, 2000, resultPo, true, 1, TAG_TYPE_HTML)
        }
    }
}