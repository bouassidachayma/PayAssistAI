package com.payassistai.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull

class MerchantRepository(
    private val dao: MerchantDao
) {
    private val _currentMerchant = MutableStateFlow<Merchant?>(null)
    val currentMerchant: StateFlow<Merchant?> = _currentMerchant.asStateFlow()

    fun setCurrentMerchant(merchant: Merchant?) {
        _currentMerchant.value = merchant
    }

    suspend fun insert(merchant: Merchant): Long = dao.insert(merchant)
    suspend fun update(merchant: Merchant) = dao.update(merchant)
    suspend fun delete(merchant: Merchant) = dao.delete(merchant)
    suspend fun getMerchantById(id: Int): Merchant? = dao.getMerchantById(id)
    suspend fun getMerchantByEmail(email: String): Merchant? = dao.getMerchantByEmail(email)
    fun getAllMerchants(): Flow<List<Merchant>> = dao.getAllMerchants()
    suspend fun getAdmin(): Merchant? = dao.getAdmin()
    suspend fun updatePassword(merchantId: Int, newHash: String) = dao.updatePassword(merchantId, newHash)

    // Seed default admin and merchants (moved from ViewModel)
    suspend fun seedDefaultData() {
        // Create admin if missing
        val admin = dao.getAdmin()
        if (admin == null) {
            dao.insert(
                Merchant(
                    name = "Admin",
                    email = "admin@mall-sfax.net",
                    passwordHash = PasswordUtils.hash("admin123"),
                    category = "Admin",
                    role = "admin"
                )
            )
        }

        // Create merchants if missing
        val existing = dao.getAllMerchants().firstOrNull()
        val hasMerchant = existing?.any { it.role == "merchant" } == true
        if (!hasMerchant) {
            val merchants = listOf(
                Merchant(name = "Carrefour", email = "carrefour@mall-sfax.net", passwordHash = "carrefour", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Zara", email = "zara@mall-sfax.net", passwordHash = "zara", category = "Shopping", role = "merchant"),
                Merchant(name = "Massimo Dutti", email = "massimodutti@mall-sfax.net", passwordHash = "massimodutti", category = "Shopping", role = "merchant"),
                Merchant(name = "Pull&Bear", email = "pullandbear@mall-sfax.net", passwordHash = "pullandbear", category = "Shopping", role = "merchant"),
                Merchant(name = "Bershka", email = "bershka@mall-sfax.net", passwordHash = "bershka", category = "Shopping", role = "merchant"),
                Merchant(name = "Stradivarius", email = "stradivarius@mall-sfax.net", passwordHash = "stradivarius", category = "Shopping", role = "merchant"),
                Merchant(name = "Oysho", email = "oysho@mall-sfax.net", passwordHash = "oysho", category = "Shopping", role = "merchant"),
                Merchant(name = "Mango", email = "mango@mall-sfax.net", passwordHash = "mango", category = "Shopping", role = "merchant"),
                Merchant(name = "Celio", email = "celio@mall-sfax.net", passwordHash = "celio", category = "Shopping", role = "merchant"),
                Merchant(name = "LC Waikiki", email = "lcwaikiki@mall-sfax.net", passwordHash = "lcwaikiki", category = "Shopping", role = "merchant"),
                Merchant(name = "Jennyfer", email = "jennyfer@mall-sfax.net", passwordHash = "jennyfer", category = "Shopping", role = "merchant"),
                Merchant(name = "Zen", email = "zen@mall-sfax.net", passwordHash = "zen", category = "Shopping", role = "merchant"),
                Merchant(name = "Lee Cooper", email = "leecooper@mall-sfax.net", passwordHash = "leecooper", category = "Shopping", role = "merchant"),
                Merchant(name = "Jules", email = "jules@mall-sfax.net", passwordHash = "jules", category = "Shopping", role = "merchant"),
                Merchant(name = "Aldo", email = "aldo@mall-sfax.net", passwordHash = "aldo", category = "Shopping", role = "merchant"),
                Merchant(name = "Sometimes", email = "sometimes@mall-sfax.net", passwordHash = "sometimes", category = "Shopping", role = "merchant"),
                Merchant(name = "Ballet", email = "ballet@mall-sfax.net", passwordHash = "ballet", category = "Shopping", role = "merchant"),
                Merchant(name = "Joliesse", email = "joliesse@mall-sfax.net", passwordHash = "joliesse", category = "Shopping", role = "merchant"),
                Merchant(name = "Rosette", email = "rosette@mall-sfax.net", passwordHash = "rosette", category = "Shopping", role = "merchant"),
                Merchant(name = "Manuella Shoes", email = "manuella@mall-sfax.net", passwordHash = "manuella", category = "Shopping", role = "merchant"),
                Merchant(name = "Karizma", email = "karizma@mall-sfax.net", passwordHash = "karizma", category = "Shopping", role = "merchant"),
                Merchant(name = "123 Shoes", email = "123shoes@mall-sfax.net", passwordHash = "123shoes", category = "Shopping", role = "merchant"),
                Merchant(name = "Omiz", email = "omiz@mall-sfax.net", passwordHash = "omiz", category = "Shopping", role = "merchant"),
                Merchant(name = "Decathlon", email = "decathlon@mall-sfax.net", passwordHash = "decathlon", category = "Shopping", role = "merchant"),
                Merchant(name = "Tutto Sport", email = "tuttosport@mall-sfax.net", passwordHash = "tuttosport", category = "Shopping", role = "merchant"),
                Merchant(name = "Yves Rocher", email = "yvesrocher@mall-sfax.net", passwordHash = "yvesrocher", category = "Health", role = "merchant"),
                Merchant(name = "Point M", email = "pointm@mall-sfax.net", passwordHash = "pointm", category = "Health", role = "merchant"),
                Merchant(name = "Ecovillage Natural Beauty", email = "ecovillage@mall-sfax.net", passwordHash = "ecovillage", category = "Health", role = "merchant"),
                Merchant(name = "Fnac", email = "fnac@mall-sfax.net", passwordHash = "fnac", category = "Shopping", role = "merchant"),
                Merchant(name = "Darty", email = "darty@mall-sfax.net", passwordHash = "darty", category = "Shopping", role = "merchant"),
                Merchant(name = "Zara Home", email = "zarahome@mall-sfax.net", passwordHash = "zarahome", category = "Shopping", role = "merchant"),
                Merchant(name = "Papa John's", email = "papajohns@mall-sfax.net", passwordHash = "papajohns", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Baguette & Baguette", email = "baguette@mall-sfax.net", passwordHash = "baguette", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "La Graine", email = "lagraine@mall-sfax.net", passwordHash = "lagraine", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Baristas", email = "baristas@mall-sfax.net", passwordHash = "baristas", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Madame Rekik", email = "madamerekik@mall-sfax.net", passwordHash = "madamerekik", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Mokador", email = "mokador@mall-sfax.net", passwordHash = "mokador", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Corndog & Cheese", email = "corndog@mall-sfax.net", passwordHash = "corndog", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Hobo", email = "hobo@mall-sfax.net", passwordHash = "hobo", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Bariolo", email = "bariolo@mall-sfax.net", passwordHash = "bariolo", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Echemi", email = "echemi@mall-sfax.net", passwordHash = "echemi", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Tawa and Co", email = "tawa@mall-sfax.net", passwordHash = "tawa", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Manekn", email = "manekn@mall-sfax.net", passwordHash = "manekn", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Uncle", email = "uncle@mall-sfax.net", passwordHash = "uncle", category = "Food & Drinks", role = "merchant"),
                Merchant(name = "Thyna Optic", email = "thynaoptic@mall-sfax.net", passwordHash = "thynaoptic", category = "Health", role = "merchant"),
                Merchant(name = "UBCI Bank", email = "ubci@mall-sfax.net", passwordHash = "ubci", category = "Services", role = "merchant")
            )

            val hashedMerchants = merchants.map { it.copy(passwordHash = PasswordUtils.hash(it.passwordHash)) }
            dao.insertAll(hashedMerchants)
        }
    }
}