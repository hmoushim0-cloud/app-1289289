package sa.factory.fawripayposcashier.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import sa.factory.fawripayposcashier.domain.usecase.*
import sa.factory.fawripayposcashier.model.FawriPayItem
import sa.factory.fawripayposcashier.model.FawriPayOrder
import sa.factory.fawripayposcashier.model.FawriPayTelemetry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FawriPayUiState(
    val catalog: List<FawriPayItem> = emptyList(),
    val filteredCatalog: List<FawriPayItem> = emptyList(),
    val selectedCategory: String = "الكل",
    val cart: List<FawriPayItem> = emptyList(),
    val telemetry: FawriPayTelemetry? = null,
    val lastOrder: FawriPayOrder? = null,
    val isCheckingOut: Boolean = false,
    val activeTab: String = "home"
)

class FawriPayViewModel(
    private val getCatalogUseCase: GetCatalogUseCase,
    private val manageCartUseCase: ManageCartUseCase,
    private val processCheckoutUseCase: ProcessCheckoutUseCase,
    private val observeHardwareTelemetryUseCase: ObserveHardwareTelemetryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FawriPayUiState())
    val uiState: StateFlow<FawriPayUiState> = _uiState.asStateFlow()

    init {
        // Observe Catalog
        getCatalogUseCase().onEach { list ->
            _uiState.update { it.copy(catalog = list, filteredCatalog = list) }
        }.launchIn(viewModelScope)

        // Observe Cart
        manageCartUseCase.cart.onEach { cartItems ->
            _uiState.update { it.copy(cart = cartItems) }
        }.launchIn(viewModelScope)

        // Observe Telemetry
        observeHardwareTelemetryUseCase().onEach { telem ->
            _uiState.update { it.copy(telemetry = telem) }
        }.launchIn(viewModelScope)
    }

    fun selectCategory(category: String) {
        _uiState.update { state ->
            val filtered = if (category == "الكل") state.catalog else state.catalog.filter { it.category == category }
            state.copy(selectedCategory = category, filteredCatalog = filtered)
        }
    }

    fun switchTab(tab: String) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun addToCart(item: FawriPayItem) = manageCartUseCase.add(item)
    fun removeFromCart(item: FawriPayItem) = manageCartUseCase.remove(item)

    fun checkout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingOut = true) }
            val order = processCheckoutUseCase()
            _uiState.update { it.copy(isCheckingOut = false, lastOrder = order, activeTab = "checkout") }
        }
    }
}
