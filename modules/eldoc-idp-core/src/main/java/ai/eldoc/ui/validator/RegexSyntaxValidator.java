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

import ai.eldoc.util.JsfUtils;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.validator.*;
import java.util.regex.Pattern;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Custom implementation of the range validator for String filed containing regex pattern
 */
@FacesValidator("regexSyntaxValidator")
public class RegexSyntaxValidator implements Validator<String> {

    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public void validate(FacesContext fc, UIComponent component, String value) throws ValidatorException {
        if (isBlank(value)) {
            // Nothing to validate
            return;
        }

        try {
            Pattern.compile(value);
        } catch (Exception e) {
            FacesMessage msg = new FacesMessage(JsfUtils.getCustomMessagesBundle(fc).getString("ai.eldoc.ui.validator.RegexSyntaxValidator.regexPatternInvalid"));
            msg.setSeverity(FacesMessage.SEVERITY_ERROR);
            throw new ValidatorException(msg);
        }
    }

}
