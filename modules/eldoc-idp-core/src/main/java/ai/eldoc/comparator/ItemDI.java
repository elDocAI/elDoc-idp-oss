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

package ai.eldoc.comparator;

import java.text.Collator;
import java.util.Comparator;
import java.util.Objects;
import ai.eldoc.data.Order;
import ai.eldoc.model.directory.DirectoryItem;
import ai.eldoc.util.I18n;


public class ItemDI implements Comparator<DirectoryItem> {

    private final int _order;
    private final Collator _ukUACollator;


    public ItemDI() {
        this(Order.ASC);
    }


    public ItemDI(int sortOrder) {
        _order = sortOrder;
        _ukUACollator = Collator.getInstance(I18n.LOCALE_UKR);
    }


    @Override
    public int compare(DirectoryItem dItem1, DirectoryItem dItem2) {
        String str1 = Objects.requireNonNullElse(dItem1.getItemNameUI(), "");
        String str2 = Objects.requireNonNullElse(dItem2.getItemNameUI(), "");
        return _order == Order.ASC ? _ukUACollator.compare(str1, str2) : _ukUACollator.compare(str2, str1);
        //return (order == ai.eldoc.AppBean.ORDER_ASC ? str1.compareTo(str2) : str2.compareTo(str1));
    }

}
