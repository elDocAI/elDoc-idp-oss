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

import ai.eldoc.model.reco.*;
import ai.eldoc.util.JsonbUtils;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.FacesConverter;
import org.apache.commons.lang3.reflect.TypeLiteral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Converts RecoPage information to/from JSON<br>
 * Example of RecoPage data info
 *
 * <pre>
 *  [
 *      {
 *          "id" : 0,               // type: integer ID of the field on the page
 *          "coords" : {
 *              "x" : 316,          // type: integer X coordinate of the field
 *              "y" : 1002,         // type: integer Y coordinate of the field
 *              "w" : 1862,         // type: integer Width of the field
 *              "h" : 57            // type: integer Height of the field
 *          },
 *          "label" : {
 *              "x" : 316,          // type: integer X coordinate of the label
 *              "y" : 982,          // type: integer Y coordinate of the label
 *              "w" : 262,          // type: integer Width of the label
 *              "h" : 30            // type: integer Height of the label
 *          },
 *          type : "TEXT",          // type: string Type of the column, corresponding to RecoDataType
 *          "cols" : [              // array of field's columns in case its a table
 *              {
 *                  "x" : 0,
 *                  "y" : 0,
 *                  "w" : 202,
 *                  "h" : 57
 *              },
 *              {..},
 *              {..}
 *          ],
 *          "anchorUnderTable" : {
 *              "x" : 316,          // type: integer X coordinate of the anchorUnderTable
 *              "y" : 972,          // type: integer Y coordinate of the anchorUnderTable
 *              "w" : 100,          // type: integer Width of the anchorUnderTable
 *              "h" : 30            // type: integer Height of the anchorUnderTable
 *          }
 *      },
 *      {..},
 *      {..}
 *  ]
 * </pre>
 */
@FacesConverter(value = "recoFormConverter")
public class RecoFormConverter implements Converter<RecoForm> {

    private static final Logger LOG = LoggerFactory.getLogger(RecoFormConverter.class);


    public static List<RecoFieldCoords> fromJson(String pageJsonValue) {
        Type listType = new TypeLiteral<ArrayList<RecoFieldCoords>>() {
        }.getType();
        return JsonbUtils.getInstance().fromJson(pageJsonValue, listType);
    }


    @Override
    public RecoForm getAsObject(FacesContext fc, UIComponent uic, String pageJsonValue) {
        LOG.warn("RecoFormConverter: Not supported for read-only property: {}", pageJsonValue);
        return null;
    }


    @Override
    public String getAsString(FacesContext fc, UIComponent uic, RecoForm recoForm) {
        List<RecoFieldCoords> pageCoords = new ArrayList<>();
        for (int pageIdx = 0; pageIdx < recoForm.getPages().size(); pageIdx++) {
            RecoPage recoPage = recoForm.getPages().get(pageIdx);
            for (int i = 0; i < recoPage.getFields().size(); i++) {
                pageCoords.add(new RecoFieldCoords(i, pageIdx, recoForm, recoPage.getFields().get(i)));
            }
        }
        return JsonbUtils.getInstance().toJson(pageCoords);
    }


    public class RecoFieldCoords {

        public int id;
        public int pageId;
        public int anchorUnderTablePageId;
        public boolean ignored;
        public Rectangle coords;
        public Rectangle label;
        public RecoFieldDataType type;
        public List<RecoFieldCoords> cols;
        public List<RecoFieldCoords> opMarks;
        public Rectangle anchorUnderTable;


        public RecoFieldCoords() {
            cols = new ArrayList<>();
            opMarks = new ArrayList<>();
        }


        public RecoFieldCoords(int id, int pageId, RecoForm recoForm, RecoField field) {
            this.id = id;
            this.pageId = pageId;
            coords = field.getValueRegion();
            if (!recoForm.isTypeAuto() || !(field.isTypeTable() && field.getProp().isAuto())) {
                label = field.getAnchorRegion();
            }
            type = field.getType();
            ignored = field.getProp().isIgnored();
            if (field.isTypeTable()) {
                cols = field.getColumns().stream().map(cField -> new RecoFieldCoords(field.getColumns().indexOf(cField), pageId, recoForm, cField)).collect(Collectors.toList());
                if ((!recoForm.isTypeAuto() || !(field.isTypeTable() && field.getProp().isAuto())) && !field.getTableProp().isAnchorUnderTableDisabled()) {
                    anchorUnderTable = field.getTableProp().getAnchorUnderTable();
                }
                if (field.getTableProp().getAnchorUnderTablePageIndex() != 0) {
                    anchorUnderTablePageId = field.getTableProp().getAnchorUnderTablePageIndex();
                }
            } else if (field.isTypeOmr()) {
                opMarks = field.getOpMarks().stream().map(cField -> new RecoFieldCoords(field.getOpMarks().indexOf(cField), pageId, recoForm, cField)).collect(Collectors.toList());
            } else if (field.isTypeTextExtendable()) {
                if (!field.getExtendableTextProp().isBottomAnchorDisabled()) {
                    anchorUnderTable = field.getExtendableTextProp().getBottomAnchor();
                }
            }
        }
    }

}
