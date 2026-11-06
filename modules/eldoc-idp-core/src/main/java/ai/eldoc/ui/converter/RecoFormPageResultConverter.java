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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Converts RecoPage information to/from JSON.
 * Example of RecoPage data info
 *
     [
        {
            "id" : 0,               // type: integer ID of the field on the page
            "coords" : {
                "x" : 316,          // type: integer X coordinate of the field
                "y" : 1002,         // type: integer Y coordinate of the field
                "w" : 1862,         // type: integer Width of the field
                "h" : 57            // type: integer Height of the field
            },
            type : "TEXT",          // type: string Type of the column, corresponding to RecoDataType
            "cols" : [              // array of field's columns in case its a table
                {
                    "x" : 0,
                    "y" : 0,
                    "w" : 202,
                    "h" : 57
                },
                {..},
                {..}
            ]
            "opMarks" : [              // array of field's Optical Marks in case its an OMR field
                {
                    "x" : 0,
                    "y" : 0,
                    "w" : 202,
                    "h" : 57
                },
                {..},
                {..}
            ]
        },
        {..},
        {..}
    ]
 */
@FacesConverter(value = "recoFormPageResultConverter")
public class RecoFormPageResultConverter implements Converter<RecoPageResult> {

    private static final Logger LOG = LoggerFactory.getLogger(RecoFormPageResultConverter.class);


    @Override
    public RecoPageResult getAsObject(FacesContext fc, UIComponent uic, String pageJsonValue) {
        LOG.warn("recoFormPageResultConverter: Not supported for read-only property: {}", pageJsonValue);
        return null;
    }


    @Override
    public String getAsString(FacesContext fc, UIComponent uic, RecoPageResult recoPage) {
        List<RecoFieldCoords> pageCoords = recoPage.getFields().stream()
                .map(field -> {
                    return new RecoFieldCoords(recoPage.getFields().indexOf(field), recoPage.getPageIdx(), field);
                }).collect(Collectors.toList());
        return JsonbUtils.getInstance().toJson(pageCoords);
    }


    public class RecoFieldCoords {

        public int id;
        public int pageId;
        public Rectangle coords;
        public RecoFieldDataType type;
        public List<List<RecoFieldCoords>> cols;
        public List<RecoFieldCoords> opMarks;


        public RecoFieldCoords() {
            cols = new ArrayList<>();
            opMarks = new ArrayList<>();
        }


        public RecoFieldCoords(int id, int pageId, RecoFieldResult field) {
            this.id = id;
            this.pageId = pageId;
            coords = field.getRegion();
            type = field.getType();
            if (field.isTypeTable()) {
                cols = new ArrayList<>();
                field.getTableData().forEach(row -> {
                    List<RecoFieldCoords> rowCoords = new ArrayList<>();
                    row.forEach(cell -> {
                        RecoFieldCoords cellCoords = new RecoFieldCoords(row.indexOf(cell), pageId, cell);
                        rowCoords.add(cellCoords);
                    });
                    cols.add(rowCoords);
                });
            } else if (field.isTypeOmr()) {
                opMarks = new ArrayList<>();
                field.getOmrData().forEach(opMark -> {
                    RecoFieldCoords opMarkCoords = new RecoFieldCoords(field.getOmrData().indexOf(opMark), pageId, opMark);
                    opMarks.add(opMarkCoords);
                });
            }
        }
    }

}
