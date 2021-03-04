package io.de4l.app.di

import android.app.Application
import dagger.BindsInstance
import dagger.Component
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Component(
    modules = [AppModule::class]
)
@InstallIn(SingletonComponent::class)
@EntryPoint
interface AppComponent {
    @Component.Builder
    interface Builder {
        fun build(): AppComponent

        @BindsInstance
        fun application(application: Application): Builder
    }
}