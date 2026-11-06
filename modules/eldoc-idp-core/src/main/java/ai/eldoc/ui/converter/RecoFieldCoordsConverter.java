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

import ai.eldoc.model.reco.RecoField;
import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.util.JsonbUtils;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIInput;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;


/**
 * Converts RecoField rectangle information to/from JSON.
 * Example of RecoField coords info
 *
 * {
 * "coords" : {
 * "x" : 316, // type: integer X coordinate of the field
 * "y" : 1002, // type: integer Y coordinate of the field
 * "w" : 1862, // type: integer Width of the field
 * "h" : 57 // type: integer Height of the field
 * },
 * "label" : {
 * "x" : 316, // type: integer X coordinate of the label
 * "y" : 972, // type: integer Y coordinate of the label
 * "w" : 100, // type: integer Width of the label
 * "h" : 30 // type: integer Height of the label
 * },
 * "cols" : [ // array of field's columns in case its a table
 * {
 * "x" : 0,
 * "y" : 0,
 * "w" : 202,
 * "h" : 57
 * },
 * {..},
 * {..}
 * ],
 * "opMarks" : [ // array of field's opMarks in case its a OMR
 * {
 * "x" : 0,
 * "y" : 0,
 * "w" : 202,
 * "h" : 57
 * },
 * {..},
 * {..}
 * ],
 * "anchorUnderTable" : {
 * "x" : 316, // type: integer X coordinate of the anchorUnderTable
 * "y" : 972, // type: integer Y coordinate of the anchorUnderTable
 * "w" : 100, // type: integer Width of the anchorUnderTable
 * "h" : 30 // type: integer Height of the anchorUnderTable
 * }
 * }
 */
@FacesConverter(value = "recoFieldCoordsConverter")
public class RecoFieldCoordsConverter implements Converter<RecoField> {

    private static final Logger LOG = LoggerFactory.getLogger(RecoFieldCoordsConverter.class);


    @Override
    public RecoField getAsObject(FacesContext fc, UIComponent uic, String coordsJsonValue) {
        RecoField recoField = (RecoField) ((UIInput) uic).getValue();
        try {
            RecoFieldCoords recoFieldNewCoords = RecoFieldCoords.fromJson(coordsJsonValue);
            recoField.setValueRegion(recoFieldNewCoords.coords);
            recoField.setAnchorRegion(recoFieldNewCoords.label);
            if (recoFieldNewCoords.cols != null) {
                for (int i = 0; i < recoFieldNewCoords.cols.size(); i++) {
                    recoField.getColumns().get(i).setValueRegion(recoFieldNewCoords.cols.get(i));
                }
            }
            if (recoField.isTypeTable()) {
                recoField.getTableProp().setAnchorUnderTable(recoFieldNewCoords.anchorUnderTable);
            } else if (recoField.isTypeTextExtendable()) {
                recoField.getExtendableTextProp().setBottomAnchor(recoFieldNewCoords.anchorUnderTable);
            }
        } catch (Exception e) {
            LOG.warn(coordsJsonValue, e);
            throw new ConverterException(new FacesMessage(e.getMessage()), e);
        }
        return recoField;
    }


    @Override
    public String getAsString(FacesContext fc, UIComponent uic, RecoField recoField) {
        return new RecoFieldCoords(recoField).toJson();
    }


    public static class RecoFieldCoords {

        public Rectangle coords;
        public Rectangle label;
        public List<Rectangle> cols;
        public List<Rectangle> opMarks;
        public Rectangle anchorUnderTable;


        public RecoFieldCoords() {
        }


        public RecoFieldCoords(RecoField field) {
            coords = field.getValueRegion();
            label = field.getAnchorRegion();
            if (field.isTypeTable()) {
                cols = field.getColumns().stream().map(RecoField::getValueRegion).toList();
                anchorUnderTable = field.getTableProp().getAnchorUnderTable();
            } else if (field.isTypeOmr()) {
                opMarks = field.getOpMarks().stream().map(RecoField::getValueRegion).toList();
            } else if (field.isTypeTextExtendable()) {
                anchorUnderTable = field.getExtendableTextProp().getBottomAnchor();
            }
        }


        public static RecoFieldCoords fromJson(String recoFieldCoordsJson) {
            return JsonbUtils.getInstance().fromJson(recoFieldCoordsJson, RecoFieldCoords.class);
        }


        public String toJson() {
            return JsonbUtils.getInstance().toJson(this);
        }
    }

}
