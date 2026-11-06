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
package ai.eldoc.model.reco;

import ai.eldoc.util.JsonbUtils;
import jakarta.json.bind.annotation.JsonbProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import static ai.eldoc.model.reco.RecoFormResult._RECO_FORM;
import static ai.eldoc.model.reco.RecoFormResult._RECO_FORM_CONFIDENCE;


/**
 * RecoFormResult DTO
 */
public class RecoFormResultDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 2174250876854679494L;

    private final RecoFormDto _recoForm;
    private final double _recoFormConfidence;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoFormResultDto(RecoFormResult recoFormResult) {
        _recoForm = new RecoFormDto(recoFormResult.getRecoForm());
        _recoFormConfidence = recoFormResult.getRecoFormConfidence();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public String toJson() {
        return JsonbUtils.getInstance().toJson(this);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @JsonbProperty(_RECO_FORM)
    public RecoFormDto getRecoForm() {
        return _recoForm;
    }


    @JsonbProperty(_RECO_FORM_CONFIDENCE)
    public double getRecoFormConfidence() {
        return _recoFormConfidence;
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RecoFormResultDto that = (RecoFormResultDto) o;
        return Double.compare(_recoFormConfidence, that._recoFormConfidence) == 0
                && Objects.equals(_recoForm, that._recoForm);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_recoForm, _recoFormConfidence);
    }
}
