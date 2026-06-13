package com.personal.apptruyen.di

import javax.inject.Qualifier

/**
 * Qualifier for the application-scoped CoroutineScope.
 * Survives configuration changes and is tied to the Application lifecycle.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
