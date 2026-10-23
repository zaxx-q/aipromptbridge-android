package com.aipromptbridge.core.keys

/** Storage for API keys, grouped into named pools (pool name == provider id by default). */
interface KeyRepository {
    fun loadKeys(poolName: String): List<KeyEntry>
    fun saveKeys(poolName: String, keys: List<KeyEntry>)
}

class InMemoryKeyRepository(initial: Map<String, List<KeyEntry>> = emptyMap()) : KeyRepository {
    private val pools = initial.toMutableMap()
    override fun loadKeys(poolName: String): List<KeyEntry> = pools[poolName].orEmpty()
    override fun saveKeys(poolName: String, keys: List<KeyEntry>) {
        pools[poolName] = keys
    }
}

/**
 * Caches one [KeyPool] per pool name so rotation state survives between requests.
 * Call [invalidate] after the user edits keys so the next request reloads them.
 */
class KeyPoolRegistry(private val repository: KeyRepository) {
    private val pools = mutableMapOf<String, KeyPool>()

    @Synchronized
    fun poolFor(name: String): KeyPool = pools.getOrPut(name) { KeyPool(repository.loadKeys(name)) }

    @Synchronized
    fun invalidate(name: String) {
        pools.remove(name)
    }
}
