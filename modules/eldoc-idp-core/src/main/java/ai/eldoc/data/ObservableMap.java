/*
 *
 *  * Copyright (C) 2022-2026 elDoc Limited
 *  *
 *  * This file is part of elDoc IDP OSS.
 *  *
 *  * elDoc IDP OSS is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU Affero General Public License as published
 *  * by the Free Software Foundation, version 3.
 *  *
 *  * elDoc IDP OSS is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 *  * General Public License for more details.
 *  *
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */
package ai.eldoc.data;

import org.apache.commons.collections4.map.AbstractMapDecorator;
import java.util.Map;


/**
 * Observable Map
 */
public class ObservableMap<K, V> extends AbstractMapDecorator<K, V> {

    private MapChangeListener<K, V> _listener;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ObservableMap(Map<K, V> map) {
        super(map);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void setChangeListener(MapChangeListener<K, V> listener) {
        _listener = listener;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    public V put(K key, V value) {
        V oldValue = super.put(key, value);
        if (_listener != null) {
            _listener.onPut(key, value, oldValue);
        }
        return oldValue;
    }


    @Override
    public V remove(Object key) {
        V oldValue = super.remove(key);
        if (_listener != null) {
            _listener.onRemove((K) key, oldValue);
        }
        return oldValue;
    }


    @Override
    public void clear() {
        super.clear();
        if (_listener != null) {
            _listener.onClear();
        }
    }


    @Override
    public void putAll(Map<? extends K, ? extends V> mapToCopy) {
        if (mapToCopy != null) {
            for (Map.Entry<? extends K, ? extends V> entry : mapToCopy.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }


    //
    // Public Interfaces --------------------------------------------------------------------------
    //
    public interface MapChangeListener<K, V> {

        void onPut(K key, V newValue, V oldValue);


        void onRemove(K key, V oldValue);


        void onClear();
    }

}
