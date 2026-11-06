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

/* Global constants */
var CONTEXT_PATH = document.head.querySelector('meta[name=contextPath]').content;

document.addEventListener('DOMContentLoaded', function () {
    // Page workflow.xhtml custom on-load logic
    if (isPage('/workflow.xhtml')) {
        try {
            var downloadFile = new URL(window.location.href).searchParams.get('downloadFile');
            if (downloadFile) {
                $('.wfForm .file-download-table td.nameColumn > a').each(function (index, linkNode) {
                    var fileLink = $(linkNode).attr('href');
                    if (fileLink && fileLink.includes(downloadFile)) {
                        window.open(fileLink, '_blank');
                    }
                });
            }
        } catch (e) {
            console.error(e);
        }
    } else if (isPage('/admin/recoForms.xhtml')) {
        // Initializes RecoForm Import logic
        try {
            document.getElementById('recoFormImportFile').addEventListener('change', processRecoFormImport);
        } catch (e) {
            console.error(e);
        }
    }

    registerEventListener_rnsSelect();
    registerEventListener_langSelect();
    registerEventListener_logoutLnk();
    registerEventListener_menuButton();
    registerEventListener_topbarMenuButton();
    registerEventListener_topbarDropdowns();
    registerEventListener_moduleItems();
    registerEventListener_moduleSelector();
});


function registerEventListener_rnsSelect() {
    document.querySelectorAll('#rnsSelect > li > a').forEach((rnsSelElement) => {
        rnsSelElement.addEventListener('click', (e) => {
            doPost('/api/uctx/cmd', { 'rns': e.currentTarget.dataset.rns || null }, () => window.location.reload());
        });
    });
}


function registerEventListener_langSelect() {
    document.querySelectorAll('#langSelect > li > a').forEach((langSelElement) => {
        langSelElement.addEventListener('click', (e) => {
            doPost('/api/uctx/cmd', {'lang': e.currentTarget.dataset.lang}, () => window.location.reload());
        });
    });
}


function registerEventListener_logoutLnk() {
    document.getElementById('logoutLnk')?.addEventListener('click', (e) => {
        doPost('/logout', null, () => openPage('/'));
    });
}


function registerEventListener_menuButton() {
    let menuButton = document.getElementById('menu-button');
    const menuLayout = document.querySelector('.layout-wrapper');
    if (!menuButton || !menuLayout) return;
    menuButton.setAttribute('aria-expanded', String(!menuLayout.classList.contains('layout-menu-static-inactive')));
    if (isPage('/') || isPage('/main.xhtml') || isPage('/files.xhtml') || isPage('/file.xhtml')) {
        // Remove event listener from the pages where it is not required
        let menuButtonClone = menuButton.cloneNode(true);
        menuButtonClone.style.pointerEvents = 'none';
        menuButtonClone.style.cursor = 'default';
        menuButtonClone.setAttribute('aria-disabled', 'true');
        menuButtonClone.tabIndex = -1;
        menuButton.parentNode.replaceChild(menuButtonClone, menuButton);
    } else {
        // Assign even listener
        menuButton.addEventListener('click', (e) => {
            e.preventDefault();
            const isHidden = menuLayout.classList.toggle('layout-menu-static-inactive');
            menuButton.classList.toggle('menu-button-rotate', isHidden);
            menuButton.setAttribute('aria-expanded', String(!isHidden));
            doPost('/api/uctx/cmd', { 'mainMenu': isHidden ? 'hide' : 'show' });
        });
    }
}


function registerEventListener_topbarMenuButton() {
    document.getElementById('topbar-menu-button')?.addEventListener('click', (e) => {
        e.preventDefault();
        const isOpen = document.body.classList.toggle('mobile-menu-open');
        e.currentTarget.setAttribute('aria-expanded', String(isOpen));
    });
}


function registerEventListener_topbarDropdowns() {
    const closeDropdowns = () => {
        document.querySelectorAll('.topbar-items > li').forEach((item) => {
            item.classList.remove('topbar-item-open');
            item.querySelector(':scope > a')?.setAttribute('aria-expanded', 'false');
        });
    };

    document.querySelectorAll('.topbar-items > li > a').forEach((toggle) => {
        toggle.addEventListener('click', (e) => {
            e.preventDefault();
            const item = e.currentTarget.parentElement;
            const isOpen = item.classList.toggle('topbar-item-open');
            e.currentTarget.setAttribute('aria-expanded', String(isOpen));
            document.querySelectorAll('.topbar-items > li').forEach((otherItem) => {
                if (otherItem !== item) {
                    otherItem.classList.remove('topbar-item-open');
                    otherItem.querySelector(':scope > a')?.setAttribute('aria-expanded', 'false');
                }
            });
        });
    });

    document.addEventListener('click', (e) => {
        if (!e.target.closest('.topbar-items')) closeDropdowns();
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            document.querySelector('.topbar-item-open > a')?.focus();
            closeDropdowns();
            document.body.classList.remove('mobile-menu-open');
            document.getElementById('topbar-menu-button')?.setAttribute('aria-expanded', 'false');
        }
    });
}


function registerEventListener_moduleItems() {
    const dfltCbs = document.querySelectorAll(".main-page-module-select .dfltCb");
    dfltCbs.forEach((cbx) => {
        cbx.addEventListener('click', (event) => {
            event.stopPropagation();
            dfltCbs.forEach(chkbx => {
                if (chkbx !== event.currentTarget) {
                    PF(chkbx.dataset.name).uncheck();
                }
            });
        });
    });

    // Using ECMAScript 2020 (ES2020) support of Optional Chaining
    let data = { 'mainMenu': 'show' };
    document.getElementById('module-esign')?.addEventListener('click', (e) => {
        data.module = e.currentTarget.dataset.module;
        data.modulePersist = document.querySelector('.main-page-module-select input[type=checkbox][aria-checked=true]')?.dataset.module;
        doPost('/api/uctx/cmd', data, () => openPage('/esign/start.xhtml'));
    });
    document.getElementById('module-bpm')?.addEventListener('click', (e) => {
        data.module = e.currentTarget.dataset.module;
        data.modulePersist = document.querySelector('.main-page-module-select input[type=checkbox][aria-checked=true]')?.dataset.module;
        doPost('/api/uctx/cmd', data, () => openPage('/bpm/start.xhtml'));
    });
    document.getElementById('module-idp')?.addEventListener('click', (e) => {
        data.module = e.currentTarget.dataset.module;
        data.modulePersist = document.querySelector('.main-page-module-select input[type=checkbox][aria-checked=true]')?.dataset.module;
        doPost('/api/uctx/cmd', data, () => openPage('/idp/start.xhtml'));
    });
    document.getElementById('module-fm')?.addEventListener('click', (e) => {
        data.module = e.currentTarget.dataset.module;
        data.modulePersist = document.querySelector('.main-page-module-select input[type=checkbox][aria-checked=true]')?.dataset.module;
        doPost('/api/uctx/cmd', data, () => openPage('/files.xhtml'));
    });
}


function registerEventListener_moduleSelector() {
    let moduleSelectButton = document.getElementById('modSel');
    if (moduleSelectButton?.classList.contains('disabled')) {
        // Module selection disabled
        return;
    }
    moduleSelectButton?.addEventListener('click', (e) => {
        doPost('/api/uctx/cmd', { 'module': null }, () => openPage('/main.xhtml'));
    });
}


$(document).on('click', "button[eldoc-print='true']", function (evt) {
    evt.preventDefault();
    var printBtn = $(this);
    var docId = printBtn.attr('eldoc-print-id');
    if (printBtn.attr('eldoc-print-type') === 'document') {
        var isTabMonitoring = false;
        var selectedState = null;
        var selectedIter = null;
        $("#f1\\:respList > div.p-tabs-panels > div[aria-hidden='false'] div[id$='tabInfo'] > span[id='tabMonitoring']").each(function () {
            // it has to be only one of such labes
            isTabMonitoring = true;
        });
        $("#f1\\:respList > div.p-tabs-panels > div[aria-hidden='false'] div[id$='tabInfo'] > label[id$='state']").each(function () {
            // it has to be only one of such labes
            selectedState = $(this).attr('state');
        });
        $("#f1\\:respList > div.p-tabs-panels > div[aria-hidden='false'] div[id$='tabInfo'] > label[id$='iter']").each(function () {
            // it has to be only one of such labes
            selectedIter = $(this).attr('iter');
        });
        printDocument(docId, isTabMonitoring, selectedState, selectedIter);
    } else if (printBtn.attr('eldoc-print-type') === 'barcode') {
        printBarCode(docId, printBtn.attr('eldoc-ext-data'));
    }
});


/**
 * Avoids submission of the form inputs on Enter press
 * @type type
 */
document.addEventListener('keydown', (evt) => {
    if (evt.key === 'Enter' && evt.target instanceof Element && evt.target.matches('form input')) {
        evt.preventDefault();
    }
});


/**
 * Retrieves and prints document
 *
 * @param docId
 * @param selectedTabIndex
 * @return
 */
function printDocument(docId, isTabMonitoring, selectedState, selectedIter) {
    var address = CONTEXT_PATH + '/workflow.xhtml';
    var wParams = 'menubar=no,toolbar=no,location=no,directories=no,status=no,scrollbars=no,resizable=no,dependent,width=960,height=700,left=30,top=10';
    $.ajax({
        url: address,
        method: 'GET',
        cache: false,
        data: {
            id: docId,
            print: true,
            state: selectedState,
            iter: selectedIter,
            mon: isTabMonitoring
        },
        dataType: 'html'
    }).done(function (response) {
        var newWin = window.open('', '_blank', wParams);
        if (!newWin || newWin.closed || typeof newWin.closed === 'undefined') {
            alert('Please enable and allow poop-up window in your browser');
            return;
        }
        newWin.document.write(response);
        newWin.document.close();
        newWin.onload = function () {
            newWin.focus();
            newWin.print();
            newWin.close();
        };
    }).fail(function (jqXHR, textStatus) {
        alert('Request failed: ' + textStatus);
    });
}


/**
 * Retrieves and prints document's barcode
 *
 * @param {String} docId
 * @param {Json} extDataString
 * @return
 */
function printBarCode(docId, extDataString) {
    var address = CONTEXT_PATH + '/api/barCode?key=' + docId;
    var extData = JSON.parse(extDataString);
    var data =
        "<html><head></head>" +
        "<body style='padding: 0; margin: 0;'>" +
        "<div style='width: 100%; height: 100%; display: table;'>" +
        "<div style='display: table-cell; vertical-align: bottom; text-align: right;'>" +
        "<div style='display:inline-block;text-align:center;'>" +
        "<span style='font-size:14pt;'>" + extData.line1 + "</span><br/>" +
        "<span style='font-size:14pt;'>" + extData.line2 + "</span><br/>" +
        "<img src='" + address + "' />" +
        "</div>" +
        "</div>" +
        "</div>" +
        "</body></html>";
    var wParams = 'menubar=no,toolbar=no,location=no,directories=no,status=no,scrollbars=no,resizable=no,dependent,width=900,height=700,left=10,top=10';
    var newWin = window.open('', '_blank', wParams);
    newWin.document.write(data);
    newWin.document.close();
    newWin.onload = function () {
        newWin.focus();
        newWin.print();
        newWin.close();
    };
}


function displayMsg(severity, msg, detail, life = 5000) {
    let msgData = {
        'summary': msg,
        'severity': severity
    };
    if (detail) {
        msgData.detail = detail;
    }
    const growlWidget = PrimeFaces.widgets['growlWV'];
    if (growlWidget) {
        growlWidget.renderMessage(msgData);
    }
    if (window.ELDoc?.showToast) {
        window.ELDoc.showToast(severity, msg, null, life);
    }
}


function displayLabelMsg(severity, labelId, life) {
    getLabel(labelId, document.body.lang, function (labelMsg) {
        displayMsg(severity, labelMsg, null, life);
    });
}


function getLabel(labelId, lang, callBack) {
    var address = CONTEXT_PATH + '/api/labels';
    $.ajax({
        url: address,
        method: 'GET',
        data: {
            labelId: labelId,
            lang: lang
        },
        dataType: 'json'
    }).done(function (data) {
        if (callBack !== undefined) {
            callBack(data.label);
        }
        return data.label;
    }).fail(function (jqXHR, textStatus) {
    });
}


function openNav() {
    document.documentElement.style.overflow = 'hidden';
    document.body.style.overflow = 'hidden';
    document.body.style.position = 'fixed';
    document.getElementById('global-overlay-area').style.display = 'block';
}


function closeNav() {
    document.documentElement.style.removeProperty('overflow');
    document.body.style.removeProperty('overflow');
    document.body.style.removeProperty('position');
    document.getElementById('global-overlay-area').style.display = 'none';
}


/**
 * Resets input value on ESC button press
 * @param {type} event
 * @param {type} inputTarget
 * @returns {Boolean}
 */
function resetInputOnEsc(event, inputTarget) {
    if (event !== null && jQuery.event.fix(event).keyCode === jQuery.ui.keyCode.ESCAPE) {
        event.preventDefault();
        inputTarget.val('');
        return false;
    }
    return true;
}


function doFtSearch(event, searchFTWidget, btnSearchRc) {
    if (resetInputOnEsc(event, searchFTWidget.jq) === false) {
        // clean search input and refresh table
        btnSearchRc();
    } else if (event !== null && jQuery.event.fix(event).keyCode === jQuery.ui.keyCode.ENTER) {
        // perform search by Enter key pressed
        event.preventDefault();
        btnSearchRc();
    }
}


function doExportDownload(targetUrl) {
    if (targetUrl !== undefined) {
        window.open(targetUrl, '_blank');
    }
}


/**
 * Used with multiple FileUpload control to execute command onthe all files are being processed
 * @param {type} fileUpload
 * @param {type} remoteCommand
 * @return {undefined}
 */
function handleMultiFileUploadRequest(fileUpload, remoteCommand) {
    if (fileUpload.files.length === 0) {
        if (remoteCommand) {
            remoteCommand();
        }
    }
}

/**
 * Check for keyCode=13 is a solution for avoiding form submission on Enter
 * @param {type} event
 * @returns {Boolean}
 */
function disableFormSubmitOnEnterPress(event) {
    //if (event.keyCode === 13 && !$(event.target).is('textarea')) {
    if (event.keyCode === 13) {
        return false;
    }
    return true;
}


/**
 * File upload button action
 * @param {type} inputFileId
 * @returns {undefined}
 */
function btnFileUploadClick(inputFileId) {
    // Clear selected before file if any
    document.getElementById(inputFileId).value = '';

    // Init file upload dialog
    document.getElementById(inputFileId).click();

    return true;
}


/**
 * Processes RecoForm Import request
 * @returns {undefined}
 */
function processRecoFormImport() {
    if (this.files.length !== 1) {
        return; // nothing to do
    }

    PF('growlWV').renderMessage({ 'summary': 'Import started...', 'severity': 'info' });

    var request = new XMLHttpRequest();
    request.open('POST', CONTEXT_PATH + '/api/recoForm/import');

    request.onload = function () {
        if (request.status === 200) {
            // Handle success
            var details = JSON.parse(request.response);

            var detailMsg = 'Total number of forms: ' + details.total;
            if (details.skipped > 0) {
                detailMsg = detailMsg + ', Skipped number of forms: ' + details.skipped;
            }

            PF('formsTblWV').filter();

            setTimeout(function () {
                PF('growlWV').renderMessage({
                    'summary': 'Forms imported with Suspended status',
                    'detail': detailMsg,
                    'severity': 'info'
                });
            }, 750);
        } else {
            // Handle error
            var serverMessage = '';
            if (request.response) {
                try {
                    serverMessage = JSON.parse(request.response).error;
                } catch (ex) {
                    console.error(ex);
                }
            } else if (request.getResponseHeader('eldoc-error')) {
                serverMessage = request.getResponseHeader('eldoc-error');
            }

            var detailMsg = 'Error: ' + request.status + ' (' + request.statusText + ')';
            if (serverMessage !== '') {
                detailMsg = detailMsg + '. ' + serverMessage;
            }

            PF('growlWV').renderMessage({
                'summary': 'Forms import error',
                'detail': detailMsg,
                'severity': 'error'
            });
        }
    };

    request.onerror = function () {
        PF('growlWV').renderMessage({
            'summary': 'Import failed',
            'severity': 'fatal'
        });
    };

    var formData = new FormData();
    formData.append('rfsFile', this.files[0]);

    request.send(formData);
}


// Find Logs table by it's ID. Use ID suffix, as it can be prefixed with JSF
let logsTableDivList = document.querySelectorAll("[id$=':logsTbl']");
if (logsTableDivList.length > 0) {
    // If Logs table located - attach event listeners
    logsTableDivList[0].addEventListener('mouseover', showLogDetails);
}


function showLogDetails(e) {
    if (!e.target.matches('span.showLogDetails')) {
        // In case event is fired on wrong element - do nothing
        return;
    }

    // Get ID of the related log entry
    let logId = e.target.getAttribute('data-id');

    if (logId) {
        let address = CONTEXT_PATH + '/api/log/' + logId + '/stacktrace';
        fetch(address)
            // Check response code
            .then((response) => {
                if (response.status === 200) {
                    return response.text();
                } else {
                    throw response.statusText;
                }
            })
            .then((responseText) => showLogData(responseText))
            .catch((error) => console.error(error));
    }
}


/**
 * Creates tooltip elements and renders it on the page
 *
 * @param {String} responseText
 * @returns {undefined}
 */
function showLogData(responseText) {
    // Remove existing log data area, if any
    document.querySelectorAll("[id$='logData']").forEach(logDetailsArea => removeNode(logDetailsArea));

    // If response text is present - render tooltip div
    if (responseText) {
        let logDataContent = document.createElement('pre');
        logDataContent.id = 'logData';
        logDataContent.className = 'log-overlay-area';
        logDataContent.innerHTML = responseText;
        logDataContent.addEventListener('mouseout', removeLogData);
        document.body.appendChild(logDataContent);
    }
}


/**
 * Event listener to remove previously rendered tooltip from page
 *
 * @param {type} e
 * @returns {undefined}
 */
function removeLogData(e) {
    if (e.target) {
        if (!e.target.matches('pre#logData')) {
            // In case event is fired on wrong element - do nothing
            return;
        }
        removeNode(e.target);
    }
}


/**
 * Helper function, that removes node from DOM
 *
 * @param {type} node
 * @returns {undefined}
 */
function removeNode(node) {
    if (node.parentNode) {
        node.parentNode.removeChild(node);
    }
}


function workflowViewNameChange() {
    let viewName = PF('wrkfViewNameWV').getSelectedValue().toLowerCase();
    let url = new URL(window.location);
    if ('all' === viewName) {
        url.searchParams.delete('view');
    } else {
        url.searchParams.set('view', viewName);
    }
    window.history.replaceState(null, null, url);
    window.location.reload();
}


function doGet(endpoint = '/api/status', callback) {
    fetch(CONTEXT_PATH + endpoint, {})
        .then(response => {
            if (!response.ok) {
                throw new Error('Response code: ' + response.status);
            }
            return response.json();
        }).then(jsonReponse => {
            if (callback) {
                callback(jsonReponse);
            }
        }).catch((error) => {
            console.error(error);  // Handle the error
        });
}


function doPost(endpoint = '/api/status', requestData, callback) {
    let requestParams = { method: 'POST' };
    if (requestData && typeof requestData === 'object') {
        requestParams.headers = {
            'Content-Type': 'application/json',
        };
        requestParams.body = JSON.stringify(requestData);
    }

    fetch(CONTEXT_PATH + endpoint, requestParams)
        .then(response => {
            if (!response.ok) {
                throw new Error('Response code: ' + response.status);
            } else if (response.status === 204) {
                return null;
            }

            const contentType = response.headers.get('Content-Type');
            if (!contentType) {
                throw new Error('Response Content-Type is missing');
            } else if (contentType.includes('application/json')) {
                return response.json();
            } else if (contentType.includes('text')) {
                return response.text();
            } else {
                throw new Error(`Unsupported content type: ${contentType}`);
            }
        }).then(jsonReponse => {
            if (callback) {
                callback(jsonReponse);
            }
        }).catch((error) => {
            console.error(error);
        });
}


/**
 * Checks if the current page is the one given as argument (considering context root as well)
 * @param {String} pageName - page name starting with leadin slash, e.g. /main.xhtml
 * @returns {Boolean} true in case the given page is the current one, false otherwise
 */
function isPage(pageName) {
    const url = new URL(window.location.href);
    if ('/' === pageName) {
        // Handling for the default page
        return CONTEXT_PATH ? (CONTEXT_PATH + pageName) === url.pathname : pageName === url.pathname;
    }
    return CONTEXT_PATH ? new RegExp('^' + CONTEXT_PATH + pageName, 'i').test(url.pathname) : new RegExp('^' + pageName, 'i').test(url.pathname);
}


/**
 * Navigates to the page given as argument (considering context root as well)
 * @param {String} pageName - page name starting with leadin slash, e.g. /main.xhtml
 * @returns {undefined}
 */
function openPage(pageName) {
    window.location.assign(CONTEXT_PATH + pageName);
}


//
// Preview Area Image Scaling related code
//
let previewAreaScale = 1;
const PREVIEW_AREA_SCALE_MIN = 0.2;
const PREVIEW_AREA_SCALE_MAX = 5;

// Utility to update scaling for all images in the current list
function previewAreaUpdateScale() {
    const ul = document.querySelector('.preview-content-area .p-datalist-content .p-datalist-data');
    if (!ul) return;

    // On first run, store the original ul height
    if (!ul.dataset.baseHeight) {
        ul.dataset.baseHeight = ul.offsetHeight;
    }
    const baseHeight = parseFloat(ul.dataset.baseHeight);

    let maxAllowedScale = PREVIEW_AREA_SCALE_MAX;

    // Find the minimal allowed scale based on image widths
    const imgs = ul.querySelectorAll('img');
    imgs.forEach(img => {
        if (!img.dataset.originalWidth || !img.dataset.originalHeight) {
            if (img.complete) {
                img.dataset.originalWidth = img.naturalWidth;
                img.dataset.originalHeight = img.naturalHeight;
            } else {
                img.addEventListener('load', previewAreaUpdateScale, { once: true });
                return;
            }
        }
        const imgAspect = img.dataset.originalWidth / img.dataset.originalHeight;
        // The scaled width for this image at current scale
        const scaledHeight = baseHeight * previewAreaScale;

        // Calculate the maximum scale for this image
        const imgMaxScale = ul.clientWidth / (img.dataset.originalWidth / img.dataset.originalHeight * baseHeight);
        if (imgMaxScale < maxAllowedScale) {
            maxAllowedScale = imgMaxScale;
        }
    });

    // Clamp and **set** the global scale so wheel-down is always responsive!
    previewAreaScale = Math.max(PREVIEW_AREA_SCALE_MIN, Math.min(previewAreaScale, maxAllowedScale, PREVIEW_AREA_SCALE_MAX));

    // Apply scaling
    const scaledHeight = baseHeight * previewAreaScale;
    ul.style.height = scaledHeight + 'px';
}

function previewAreaInjectPaginatorCustomButtons() {
    const paginator = document.querySelector('.preview-content-area .p-datalist .p-paginator.p-paginator-top');
    if (!paginator) return;

    // Helper to create one button
    function makeBtn({ suffix, iconClass, label }) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = [
            'p-button', 'p-button-icon-only', 'p-button-outlined', 'p-button-secondary',
            `scale-${suffix}`
        ].join(' ');
        btn.innerHTML = `
      <span class="p-button-icon-left p-c ${iconClass}"></span>
      <span class="p-button-text p-c">${label}</span>
    `;
        return btn;
    }

    // Define your two buttons
    const wrapper = document.createElement('div');
    wrapper.className = 'paginator-custom-buttons';

    [
        { suffix: 'minus', iconClass: 'pi pi-minus', label: '-' },
        { suffix: 'plus', iconClass: 'pi pi-plus', label: '+' }
    ].forEach(spec => wrapper.appendChild(makeBtn(spec)));

    paginator.appendChild(wrapper);
}

function previewAreaRegisterEvents() {
    // Handle wheel (with Ctrl) globally using event capturing
    document.addEventListener('wheel', function (e) {
        // Only respond if Ctrl is held
        if (!e.ctrlKey) return;
        // Find the ul
        const ul = document.querySelector('.preview-content-area .p-datalist-content .p-datalist-data');
        if (!ul) return;
        // Check if the event happened on/inside the ul
        if (ul === e.target || ul.contains(e.target)) {
            e.preventDefault();
            if (e.deltaY < 0) previewAreaScale = Math.min(previewAreaScale + 0.2, PREVIEW_AREA_SCALE_MAX);
            else previewAreaScale = Math.max(previewAreaScale - 0.2, PREVIEW_AREA_SCALE_MIN);
            previewAreaUpdateScale();
        }
    }, { passive: false, capture: true });

    // Button handlers (delegated in case buttons are refreshed as well)
    document.addEventListener('click', function (e) {
        const btn = e.target.closest('.preview-content-area .p-datalist .p-paginator .paginator-custom-buttons .scale-plus, .preview-content-area .p-datalist .p-paginator .paginator-custom-buttons .scale-minus');
        if (!btn) return;

        if (btn.classList.contains('scale-plus')) {
            previewAreaScale = Math.min(previewAreaScale + 0.2, PREVIEW_AREA_SCALE_MAX);
        } else {
            previewAreaScale = Math.max(previewAreaScale - 0.2, PREVIEW_AREA_SCALE_MIN);
        }
        previewAreaUpdateScale();
    });
}


//
// Using Viewer.js
//
const imagesViewerSelectorBase = '.preview-content-area .p-datalist-content';
const imagesViewerSelectorSuffix = '.p-datalist-data';
let imagesViewer = null;

function initImagesViewer() {
    const galleryEl = document.querySelector(imagesViewerSelectorBase + ' ' + imagesViewerSelectorSuffix);
    if (!galleryEl) return;

    // tear down any old Viewer
    if (imagesViewer) {
        imagesViewer.destroy();
        imagesViewer = null;
    }

    // build a fresh one
    imagesViewer = new Viewer(galleryEl, {
        toolbar: {
            zoomIn: { show: true, size: 'large' },
            zoomOut: { show: true, size: 'large' },
            oneToOne: { show: true, size: 'large' },
            reset: { show: true, size: 'large' },
            rotateLeft: { show: true, size: 'large' },
            rotateRight: { show: true, size: 'large' }
        },
        navbar: false,
        title: false,
        zoomRatio: 0.25,
        initialCoverage: 1,
    });
}

document.addEventListener('DOMContentLoaded', () => {
    if (!isPage('/workflow.xhtml') && !isPage('/file.xhtml')) {
        // Not a document or file page, image preview controls are not needed
        return;
    }

    // Add Preview Area Image Scaling related events on first load
    previewAreaInjectPaginatorCustomButtons();
    previewAreaUpdateScale();
    previewAreaRegisterEvents();
    initImagesViewer();

    // watch for the container appearing or disappearing
    const obs = new MutationObserver(muts => {
        for (const m of muts) {
            // look for additions
            m.addedNodes.forEach(node => {
                if (node.matches?.(imagesViewerSelectorBase) || node.querySelector?.(imagesViewerSelectorBase) || node.matches?.(imagesViewerSelectorSuffix)) {
                    // If the images viewer container is added, re-initialize the viewer
                    previewAreaInjectPaginatorCustomButtons();
                    previewAreaUpdateScale();
                    previewAreaRegisterEvents();
                    initImagesViewer();
                }
            });
        }
    });

    obs.observe(document.body, {
        childList: true,
        subtree: true
    });
});
