package com.crypto.arbitrage.utilite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A custom multimap implementation that uses a HashMap for key management
 * and an ArrayList for storing multiple values per key.
 *
 * This structure ensures fast lookups by key (O(1) average time complexity)
 * while allowing multiple values to be associated with a single key.
 *
 * @param <K> the type of keys maintained by this multimap
 * @param <V> the type of mapped values
 */
public class HashArrayListMultiMap<K, V> {
    private final Map<K, List<V>> map;


    public HashArrayListMultiMap() {
        this.map = new HashMap<>();
    }

    // Add value to the multimap
    public void put(K key, V value) {
        if (!map.containsKey(key)) {
            map.put(key, new ArrayList<>());
        }
        map.get(key).add(value);
    }

    // Get the list of values for a key
    public List<V> get(K key) {
        return map.getOrDefault(key, new ArrayList<>());
    }

    // Remove a value for a specific key
    public boolean remove(K key, V value) {
        if (map.containsKey(key)) {
            List<V> values = map.get(key);
            if (values.remove(value)) {
                if (values.isEmpty()) {
                    map.remove(key);
                }
                return true;
            }
        }
        return false;
    }

    // Check if the map contains a key
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    // Get all entries (key-value pairs)
    public Map<K, List<V>> getEntries() {
        return map;
    }

    // Size of the map
    public int size() {
        return map.size();
    }
    
    // Clear the map
    public void clear() {
        map.clear();
    }

    public List<V> values() {
        List<V> allValues = new ArrayList<>();
        for (List<V> valueList : map.values()) {
            allValues.addAll(valueList);
        }
        return allValues;
    }
}