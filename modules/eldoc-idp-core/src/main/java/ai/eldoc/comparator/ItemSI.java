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

import jakarta.faces.model.SelectItem;
import java.text.Collator;
import java.util.Comparator;
import java.util.Objects;
import ai.eldoc.util.I18n;


/**
 * SelectItem ASC sorting
 */
public class ItemSI implements Comparator<SelectItem> {

    Collator ukUACollator;


    public ItemSI() {
        ukUACollator = Collator.getInstance(I18n.LOCALE_UKR);
    }


    @Override
    public int compare(SelectItem si1, SelectItem si2) {
        return ukUACollator.compare(Objects.requireNonNullElse(si1.getLabel(), ""), Objects.requireNonNullElse(si2.getLabel(), ""));
    }
}
