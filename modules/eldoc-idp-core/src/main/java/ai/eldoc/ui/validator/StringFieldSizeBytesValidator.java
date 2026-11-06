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

import ai.eldoc.context.LocaleContext;
import ai.eldoc.exceptions.BadArgumentException;
import ai.eldoc.util.FileUtils;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.validator.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;


/**
 * String Field Size Bytes Validator<br>
 * Used with string fields which support RichText Editor as such field may contain embedded images, etc., thus require additional validation in size
 */
@FacesValidator("stringFieldSizeBytesValidator")
public class StringFieldSizeBytesValidator implements Validator<String> {

    private static final Logger LOG = LoggerFactory.getLogger(StringFieldSizeBytesValidator.class);


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public void validate(FacesContext fc, UIComponent component, String value) throws ValidatorException {
        try {
            // Retrieve the maxFieldSizeBytes attribute from the component
            Object maxSizeAttr = component.getAttributes().get("maxFieldSizeBytes");
            int maxSize = (maxSizeAttr instanceof Number maxSizeAttrNumber) ? maxSizeAttrNumber.intValue() : 0;
            if (maxSize == 0) {
                throw new BadArgumentException("Attribute [maxFieldSizeBytes] has to be set on the component to the valid Integer value greater than 0");
            }

            if (value != null && !value.isEmpty()) {
                int fieldSize = value.getBytes(StandardCharsets.UTF_8).length;
                if (fieldSize > maxSize) {
                    String msg = MessageFormat.format(LocaleContext.getInstance().getLabel("validation.fieldSizeBytesExceeded"),
                            FileUtils.bytesToDisplayString(fieldSize - maxSize));

                    String msgDetails = MessageFormat.format(LocaleContext.getInstance().getLabel("validation.fieldSizeBytesExceeded.details"),
                            FileUtils.bytesToDisplayString(maxSize),
                            FileUtils.bytesToDisplayString(fieldSize));

                    if (component.getAttributes().get("label") instanceof String label && !label.isBlank()) {
                        msg = label.concat(": ").concat(msg);
                    }

                    throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, msgDetails));
                }
            }
        } catch (ValidatorException ve) {
            throw ve;
        } catch (Exception e) {
            LOG.warn("Validation failed, fallback to validation error", e);
            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR, LocaleContext.getInstance().getLabel("msg.error.general"), null));
        }

    }
}
