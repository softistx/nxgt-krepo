package com.softistx.oauth.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@Module
@ComponentScan("com.softistx.oauth")
class AppModule

@KoinApplication(modules = [AppModule::class])
object KtorApplication
