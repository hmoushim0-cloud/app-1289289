package sa.factory.fawripayposcashier.di

import sa.factory.fawripayposcashier.data.repository.FawriPayRepositoryImpl
import sa.factory.fawripayposcashier.domain.repository.FawriPayRepository
import sa.factory.fawripayposcashier.domain.usecase.*
import sa.factory.fawripayposcashier.hardware.HardwareSensorDriver
import sa.factory.fawripayposcashier.ui.viewmodel.FawriPayViewModel

class DiContainer {
    val sensorDriver = HardwareSensorDriver()
    val repository: FawriPayRepository = FawriPayRepositoryImpl(sensorDriver)

    val getCatalogUseCase = GetCatalogUseCase(repository)
    val manageCartUseCase = ManageCartUseCase(repository)
    val processCheckoutUseCase = ProcessCheckoutUseCase(repository)
    val observeHardwareTelemetryUseCase = ObserveHardwareTelemetryUseCase(repository)

    fun provideViewModel(): FawriPayViewModel = FawriPayViewModel(
        getCatalogUseCase,
        manageCartUseCase,
        processCheckoutUseCase,
        observeHardwareTelemetryUseCase
    )
}
