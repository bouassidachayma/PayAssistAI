package com.payassistai.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payassistai.app.data.Merchant
import com.payassistai.app.data.MerchantRepository
import com.payassistai.app.data.PasswordUtils
import com.payassistai.app.data.SessionManager
import com.payassistai.app.data.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val merchantRepository: MerchantRepository,
    private val transactionRepository: TransactionRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    val currentMerchant: StateFlow<Merchant?> = merchantRepository.currentMerchant

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _merchants = MutableStateFlow<List<Merchant>>(emptyList())
    val merchants: StateFlow<List<Merchant>> = _merchants.asStateFlow()

    init {
        loadCurrentMerchant()
        // Seed default data
        viewModelScope.launch {
            merchantRepository.seedDefaultData()
            // If admin, load merchants
            if (isAdmin()) {
                loadAllMerchants()
            }
        }
    }

    private fun loadCurrentMerchant() {
        viewModelScope.launch {
            val userId = sessionManager.getUserId()
            if (userId != -1) {
                val merchant = merchantRepository.getMerchantById(userId)
                merchantRepository.setCurrentMerchant(merchant)
                _isLoggedIn.value = true
                if (merchant?.role == "admin") {
                    loadAllMerchants()
                }
            } else {
                _isLoggedIn.value = false
                merchantRepository.setCurrentMerchant(null)
            }
        }
    }

    fun loadAllMerchants() {
        viewModelScope.launch {
            merchantRepository.getAllMerchants().collect {
                _merchants.value = it
            }
        }
    }

    fun login(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _error.value = null
            try {
                val normalizedEmail = email.trim().lowercase()
                val merchant = merchantRepository.getMerchantByEmail(normalizedEmail)
                if (merchant != null && PasswordUtils.matches(password, merchant.passwordHash) && merchant.isActive) {
                    sessionManager.saveSession(merchant.id, merchant.role)
                    merchantRepository.setCurrentMerchant(merchant)
                    _isLoggedIn.value = true
                    if (merchant.role == "admin") {
                        loadAllMerchants()
                    }
                    callback(true, null)
                } else {
                    val msg = "Invalid email or password"
                    _error.value = msg
                    callback(false, msg)
                }
            } catch (e: Exception) {
                val msg = "Login failed: ${e.message}"
                _error.value = msg
                callback(false, msg)
            }
        }
    }

    fun logout() {
        sessionManager.clearSession()
        merchantRepository.setCurrentMerchant(null)
        _isLoggedIn.value = false
        _merchants.value = emptyList()
        _error.value = null
    }

    fun changePassword(
        oldPassword: String,
        newPassword: String,
        callback: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val merchant = currentMerchant.value
            if (merchant == null) {
                callback(false, "Not logged in")
                return@launch
            }
            if (!PasswordUtils.matches(oldPassword, merchant.passwordHash)) {
                callback(false, "Current password is incorrect")
                return@launch
            }
            if (newPassword.length < 4) {
                callback(false, "New password must be at least 4 characters")
                return@launch
            }
            if (PasswordUtils.matches(newPassword, merchant.passwordHash)) {
                callback(false, "New password must be different")
                return@launch
            }
            try {
                val newHash = PasswordUtils.hash(newPassword)
                merchantRepository.updatePassword(merchant.id, newHash)
                merchantRepository.setCurrentMerchant(merchant.copy(passwordHash = newHash))
                callback(true, null)
            } catch (e: Exception) {
                callback(false, "Failed to update password: ${e.message}")
            }
        }
    }

    fun isAdmin(): Boolean = currentMerchant.value?.role == "admin"

    fun addMerchant(
        name: String,
        email: String,
        password: String,
        category: String,
        callback: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val normalizedEmail = email.trim().lowercase()

                if (normalizedEmail.isBlank()) {
                    callback(false, "Email is required")
                    return@launch
                }

                if (!EMAIL_PATTERN.matches(normalizedEmail)) {
                    callback(false, "Please enter a valid email address")
                    return@launch
                }

                val existing = merchantRepository.getMerchantByEmail(normalizedEmail)
                if (existing != null) {
                    callback(false, "A merchant with this email already exists")
                    return@launch
                }

                val merchant = Merchant(
                    name = name,
                    email = normalizedEmail,
                    passwordHash = PasswordUtils.hash(password),
                    category = category,
                    role = "merchant"
                )
                merchantRepository.insert(merchant)
                loadAllMerchants()
                callback(true, null)
            } catch (e: Exception) {
                val msg = "Failed to add merchant: ${e.message}"
                _error.value = msg
                callback(false, msg)
            }
        }
    }

    fun deleteMerchant(merchantId: Int) {
        viewModelScope.launch {
            try {
                val merchant = merchantRepository.getMerchantById(merchantId)
                if (merchant != null) {
                    transactionRepository.deleteTransactionsForMerchant(merchant.id)
                    merchantRepository.delete(merchant)
                    loadAllMerchants()
                }
            } catch (e: Exception) {
                _error.value = "Failed to delete merchant: ${e.message}"
            }
        }
    }
}