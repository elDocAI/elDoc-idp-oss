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

/**
 * Shows password validation popup with details on password requirements
 * 
 * @param {String} activatedInputVW Name of the PrimeFaces WidgetVar for password input (either password or repeat), which should be used as
 * anchor for showing validation popup
 * @param {String} passwordInputWV Name of PrimeFaces WidgetVar, that can be used to access password input
 * @param {String|undefined} passwordRepeatWV (Optional) Name of PrimeFaces WidgetVar, that can be used to access password repeat input
 */
function showPasswordValidationPopup(activatedInputVW, passwordInputWV, passwordRepeatWV) {
    const popup = document.getElementById("password-strength-popup");

    const pwdInput = PF(activatedInputVW).getJQ().get()[0];

    setTimeout(() => {
        checkPasswordCriteriaAndUpdatePopup(passwordInputWV, passwordRepeatWV);
        popup.style.display = 'block';
        const rect = pwdInput.getBoundingClientRect();
        popup.style.top = rect.bottom + window.scrollY + 'px';
        popup.style.left = rect.left + window.scrollX + 'px';
    }, 200);
}

/**
 * Hides password validation popup
 */
function hidePasswordValidationPopup() {
    const popup = document.getElementById("password-strength-popup");
    setTimeout(() => popup.style.display = 'none', 200);
}

/**
 * Validates new password inputs and provides feedback by updating password validation popup
 * 
 * @param {String} inputWV1 Name of PrimeFaces WidgetVar, that can be used to access first password input
 * @param {String} inputWV2 Name of PrimeFaces WidgetVar, that can be used to access second password input
 */
function checkPasswordCriteriaAndUpdatePopup(inputWV1, inputWV2) {
    const popup = document.getElementById("password-strength-popup");
    if (!popup) {
        return;
    }

    const pwd = PF(inputWV1).getJQ().val();
    const confirmPwd = inputWV2 ? PF(inputWV2).getJQ().val() : pwd;

    const criteria = {
        uppercase: /[A-Z]/.test(pwd),
        lowercase: /[a-z]/.test(pwd),
        number: /[0-9]/.test(pwd),
        special: /[^A-Za-z0-9]/.test(pwd),
        length: pwd.length >= 8,
        match: pwd && pwd === confirmPwd
    };

    for (const [key, valid] of Object.entries(criteria)) {
        const li = popup.querySelector(`li[data-check="${key}"]`);
        if (li) {
            li.classList.toggle("valid", valid);
        }
    }
}