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
package ai.eldoc.reco;

import ai.eldoc.model.reco.*;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Performs matching of the single field on page with AI processing results
 */
public class RecoFieldAiProcessor extends AbstractRecoFieldProcessor {


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoFieldAiProcessor(RecoDocPageProcessor parent, RecoField recoField, AbstractRecoFormProcessor recoFormProcessor) {
        super(parent, recoField, recoFormProcessor);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public RecoFieldResult process() {
        RecoFieldResult rfr = initRecoFieldResult();
        rfr.setMatchConfidence(RecoFieldResult.FIELD_NOT_FOUND_CONFIDENCE); // Match confidence will be updated on successfull matching

        processFieldResult(rfr);
        if (!RecoFormResultUtils.isMatched(rfr)) {
            return null;
        }
        return rfr;
    }


    @Override
    protected void processTextFieldResult(RecoFieldResult rfr) {
        RecoFieldResult aiField = matchField();

        if (aiField == null) {
            return;
        }

        rfr.setAnchorRegion(aiField.getAnchorRegion());
        rfr.setRegion(aiField.getRegion());
        rfr.setValue(aiField.getValue());
        rfr.setMatchConfidence(100);
        rfr.setValueConf(aiField.getValueConf());
        getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_FIELD, "", rfr.getAnchorRegion(), RecoDebugger.RECT_TYPE.ANCHOR_RECT);
        getRecoDebugger().log(
                RecoDebugger.ENTRY_TYPE_FIELD,
                "Field value:",
                rfr.getRegion(),
                RecoDebugger.RECT_TYPE.VALUE_RECT,
                rfr.getValue());
    }


    @Override
    protected void processOmrFieldResult(RecoFieldResult rfr) {
        RecoFieldResult aiField = matchField();

        if (aiField == null) {
            return;
        }

        rfr.setAnchorRegion(aiField.getAnchorRegion());
        rfr.setRegion(aiField.getRegion());
        rfr.setMatchConfidence(100);
        rfr.setValueConf(aiField.getValueConf());

        RecoFieldResult opMarkResult = RecoFieldResult.createDefault(getRecoFormProcessor().getRecoForm(), getRecoField());
        rfr.getOmrData().add(opMarkResult);
        opMarkResult.setName(getRecoField().getOpMarks().get(0).getName());
        opMarkResult.setType(getRecoField().getOpMarks().get(0).getType());
        opMarkResult.setRegion(rfr.getRegion());
        opMarkResult.setValueConf(aiField.getOmrData().get(0).getValueConf());

        opMarkResult.setValue(aiField.getOmrData().get(0).getValue());
        getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_FIELD, "", rfr.getAnchorRegion(), RecoDebugger.RECT_TYPE.ANCHOR_RECT);
        getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_FIELD, "Optical mark found, value:",
                opMarkResult.getRegion(),
                RecoDebugger.RECT_TYPE.VALUE_RECT,
                opMarkResult.getValue());
    }


    @Override
    protected void processTableFieldResult(RecoFieldResult rfr) {
        RecoFieldResult locatedTable = matchField();

        if (locatedTable == null) {
            // Table was not found
            return;
        }

        rfr.setMatchConfidence(100);
        rfr.setRegion(locatedTable.getRegion());

        List<List<RecoFieldResult>> tableData = locatedTable.getTableData();

        tableData = tableData.stream()
                .map(this::matchTableColumns)
                .collect(Collectors.toList());

        rfr.getTableData().addAll(tableData);

        getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_FIELD, "Field found");
    }


    private List<RecoFieldResult> matchTableColumns(List<RecoFieldResult> tableRow) {
        return RecoFormResultUtils.reorderResultCols(getRecoFormProcessor().getRecoForm(), getRecoField(), tableRow);
    }


    protected RecoFieldResult matchField() {
        RecoPageResult pageResult = getRecoFormProcessor().getRecoFormResultImported().getPages().get(getRecoWfPage().getRecoMetaPage().getPageIdx() - getRecoFormProcessor().getRecoMetaFragment().getPageStart());
        return pageResult.getFields().stream()
                .filter(resultCandidate -> resultCandidate.getType() == getRecoField().getType())
                .filter(resultCandidate -> resultCandidate.getName().equals(getRecoField().getName()))
                .findFirst().orElse(null);
    }


    @Override
    protected void processImageFieldResult(RecoFieldResult rfr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
