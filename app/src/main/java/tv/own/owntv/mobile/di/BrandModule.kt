package tv.own.owntv.mobile.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import tv.own.owntv.mobile.login.BrandAccount
import tv.own.owntv.mobile.login.LoginViewModel
import tv.own.owntv.mobile.login.PanelClient
import tv.own.owntv.mobile.login.PanelSync
import tv.own.owntv.mobile.login.SessionStore
import tv.own.owntv.mobile.login.XtreamAuthenticator

/** 8K PRO: the signed-in account, the activation panel and the login screen. */
val brandModule = module {
    single { SessionStore(androidContext()) }
    singleOf(::XtreamAuthenticator)
    single { tv.own.owntv.mobile.login.BackupStore(androidContext()) }
    single { PanelClient(get()) }
    single { tv.own.owntv.mobile.brand.BackupCatalog(get()) }
    single { BrandAccount(get(), get(), get()) }
    single { PanelSync(get(), get(), get()) }
    single { tv.own.owntv.mobile.login.PlaybackStats(get(), get()) }
    single { tv.own.owntv.mobile.brand.BrandUpdater(androidContext()) }
    viewModelOf(::LoginViewModel)
}
