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
package ai.eldoc.ui.validator;

import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.validator.*;


/**
 * Custom implementation of the range validator for Integer field
 */
@FacesValidator("integerDinamicRangeValidator")
public class IntegerDinamicRangeValidator implements Validator<Integer> {

    //
    // Constructor --------------------------------------------------------------------------------
    //
    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public void validate(FacesContext fc, UIComponent component, Integer value) throws ValidatorException {

//        Object minValue = component.getAttributes().get("minValue");
//        Object maxValue = component.getAttributes().get("maxValue");
        
//        String propName = (String) component.getAttributes().get("label");
//        if (value < getPropIntMin(propName) || (Integer) value > getPropIntMax(propName)) {
//            String messageTemplate = Util.getStandardMessageTemplate(fc, "jakarta.faces.validator.LongRangeValidator.NOT_IN_RANGE");
//            Util.postValidationError(fc, component, format(messageTemplate, getPropIntMin(propName), getPropIntMax(propName), propName));
//        }

//        matcher = pattern.matcher(value.toString());
//        if (!matcher.matches()) {
//            FacesMessage msg = new FacesMessage("E-mail validation failed.", "Invalid E-mail format.");
//            msg.setSeverity(FacesMessage.SEVERITY_ERROR);
//            throw new ValidatorException(msg);
//        }
    }
    //
    // Getters & Setters --------------------------------------------------------------------------
    //
}
