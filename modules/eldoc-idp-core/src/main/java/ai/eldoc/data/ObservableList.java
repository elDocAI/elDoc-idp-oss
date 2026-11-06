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

import org.apache.commons.collections4.list.AbstractListDecorator;
import java.util.Collection;
import java.util.List;

/**
 * Observable List
 */
public class ObservableList<E> extends AbstractListDecorator<E> {

    private ListChangeListener<E> _listener;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ObservableList(List<E> list) {
        super(list);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void setChangeListener(ListChangeListener<E> listener) {
        _listener = listener;
    }


    //
    // Overridden methods -------------------------------------------------------------------------
    //
    @Override
    public boolean add(E object) {
        boolean result = super.add(object);
        if (result && _listener != null) {
            _listener.onAdd(size() - 1, object);
        }
        return result;
    }


    @Override
    public void add(int index, E object) {
        super.add(index, object);
        if (_listener != null) {
            _listener.onAdd(index, object);
        }
    }


    @Override
    public boolean addAll(Collection<? extends E> coll) {
        if (coll == null || coll.isEmpty()) {
            return false;
        }
        int startIndex = size();
        boolean result = super.addAll(coll);
        if (result && _listener != null) {
            int i = startIndex;
            for (E element : coll) {
                _listener.onAdd(i++, element);
            }
        }
        return result;
    }


    @Override
    public boolean addAll(int index, Collection<? extends E> coll) {
        if (coll == null || coll.isEmpty()) {
            return false;
        }
        boolean result = super.addAll(index, coll);
        if (result && _listener != null) {
            int i = index;
            for (E element : coll) {
                _listener.onAdd(i++, element);
            }
        }
        return result;
    }


    @Override
    public E set(int index, E object) {
        E oldValue = super.set(index, object);
        if (_listener != null) {
            _listener.onSet(index, object, oldValue);
        }
        return oldValue;
    }


    @Override
    public E remove(int index) {
        E oldValue = super.remove(index);
        if (_listener != null) {
            _listener.onRemove(index, oldValue);
        }
        return oldValue;
    }


    @Override
    public boolean remove(Object object) {
        int index = indexOf(object);
        if (index != -1) {
            remove(index);
            return true;
        }
        return false;
    }


    @Override
    public boolean removeAll(Collection<?> coll) {
        boolean modified = false;
        if (coll != null) {
            for (Object obj : coll) {
                if (remove(obj)) {
                    modified = true;
                }
            }
        }
        return modified;
    }


    @Override
    public boolean retainAll(Collection<?> coll) {
        boolean modified = false;
        if (coll != null) {
            for (int i = size() - 1; i >= 0; i--) {
                if (!coll.contains(get(i))) {
                    remove(i);
                    modified = true;
                }
            }
        }
        return modified;
    }


    @Override
    public void clear() {
        super.clear();
        if (_listener != null) {
            _listener.onClear();
        }
    }


    //
    // Public Interfaces --------------------------------------------------------------------------
    //
    public interface ListChangeListener<E> {

        void onAdd(int index, E newValue);

        void onSet(int index, E newValue, E oldValue);

        void onRemove(int index, E oldValue);

        void onClear();
    }

}
