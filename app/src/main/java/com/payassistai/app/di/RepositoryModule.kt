package com.payassistai.app.di

import com.payassistai.app.data.ChatMessageDao
import com.payassistai.app.data.ChatRepository
import com.payassistai.app.data.ChatSessionDao
import com.payassistai.app.data.MerchantDao
import com.payassistai.app.data.MerchantRepository
import com.payassistai.app.data.TransactionDao
import com.payassistai.app.data.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideChatRepository(
        messageDao: ChatMessageDao,
        sessionDao: ChatSessionDao
    ): ChatRepository = ChatRepository(messageDao, sessionDao)

    @Provides
    @Singleton
    fun provideTransactionRepository(dao: TransactionDao): TransactionRepository =
        TransactionRepository(dao)

    @Provides
    @Singleton
    fun provideMerchantRepository(dao: MerchantDao): MerchantRepository =
        MerchantRepository(dao)
}