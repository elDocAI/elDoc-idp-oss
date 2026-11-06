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
package ai.eldoc.ui.converter;

import ai.eldoc.model.reco.Rectangle;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.*;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Rectangle To String Converter
 */
@FacesConverter(value = "rectangleToStringConverter")
public class RectangleToStringConverter implements Converter<Rectangle> {

    @Override
    public Rectangle getAsObject(FacesContext context, UIComponent component, String value) {
        if (isBlank(value)) {
            return new Rectangle();
        }
        try {
            return Rectangle.fromString(value);
        } catch (Exception e) {
            throw new ConverterException(e);
        }
    }


    @Override
    public String getAsString(FacesContext context, UIComponent component, Rectangle rect) {
        if (rect == null) {
            return "";
        }
        return rect.toString();
    }

}
