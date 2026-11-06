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

package ai.eldoc.ui.view;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.dao.FormDefinitionDao;
import ai.eldoc.dao.NumSequenceDao;
import ai.eldoc.model.FormDefinition;
import ai.eldoc.model.NumSequence;
import ai.eldoc.util.JsfUtils;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.validator.ValidatorException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.ViewScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;


@Named(value = "numSeqController")
@ViewScoped
public class NumSequenceController implements Serializable {

    @Serial
    private static final long serialVersionUID = -385675696762387508L;
    private static final Logger LOG = LoggerFactory.getLogger(NumSequenceController.class);

    @Inject
    private NumSequenceDao _numSequenceDao;

    @Inject
    private FormDefinitionDao _formDefDao;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private PageController _pageController;

    private NumSequence _selectedNumSequenceItem;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void editItem(NumSequence scItem) {
        _selectedNumSequenceItem = scItem;
    }


    public void validatorCounterFormat(FacesContext fc, UIComponent component, Object data) throws ValidatorException {
        if (data != null) {
            String counterVText = String.valueOf(data);

            boolean dublicatedVText = _numSequenceDao.findAll().stream()
                    .filter(numSeq -> !numSeq.getId().equals(_selectedNumSequenceItem.getId()))
                    .anyMatch(numSeq -> Objects.equals(numSeq.getVText(), counterVText));

            if (dublicatedVText) {
                String msg = MessageFormat.format(_localeContext.getLabel("numSequence.msg.formatAlreadyDefined"), counterVText);
                JsfUtils.postValidationError(fc, component, msg);
            }
        }
    }


    public void scDealogSave() {
        try {
            if (!_numSequenceDao.updateOrCreate(null, _selectedNumSequenceItem)) {
                throw new ElDocRuntimeException("NumSequence item save error");
            }
        } catch (Exception e) {
            LOG.error(_selectedNumSequenceItem.getId(), e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        } finally {
            _selectedNumSequenceItem = null;
        }
    }


    public void scDealogCancel() {
        _selectedNumSequenceItem = null;
    }


    public FormDefinition getAssignedFormDefinition(String counterName) {
        return _formDefDao.findByWebId(counterName, true, null);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    /**
     * Returns sorted list (sorted by name in human-friendly format: fd1, fd2, fd11, fd20)
     *
     * @return
     */
    public List<NumSequence> getDataProvider() {
        List<NumSequence> result = _numSequenceDao.findAll();
        result.removeIf(numSeq -> !FormDefinition.FORM_ID_RECO.equals(numSeq.getName()));
        return result;
    }


    public NumSequence getSelectedNumSeq() {
        return _selectedNumSequenceItem;
    }


    public String getNextValueCalculated(String counterName) {
        return _numSequenceDao.getValue(counterName, false);
    }
}
