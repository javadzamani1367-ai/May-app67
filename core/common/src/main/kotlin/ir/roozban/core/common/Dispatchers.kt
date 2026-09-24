package ir.roozban.core.common

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: RoozbanDispatchers)

enum class RoozbanDispatchers { Default, IO }
