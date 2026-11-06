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

var letterShapes = [];
var selectedLetter;

const recoFieldSelector = "\\:ed1";
const recoFieldInputSelector = "\\:i1";
const recoFieldCoordsSelector = "\\:coords";

function initRecoDocViewer() {

    // Init text fields
    document.querySelectorAll("div.cf-reco-text-field").forEach(field => {
        if (!field.dataset.fieldInitialized) {
            initViewerTextField(field);
            field.dataset.fieldInitialized = true;
        }
    });

    // Init table fields
    document.querySelectorAll("div.cf-reco-table").forEach(field => {
        if (!field.dataset.fieldInitialized) {
            initViewerTableField(field);
            field.dataset.fieldInitialized = true;
        }
    });
}


function isIE() {
    return /MSIE \d|Trident.*rv:/.test(navigator.userAgent);
}


function initRecoDocPreview() {
    var container = document.getElementById("pageViewContainer");

    if (!container || isIE()) {
        // RecoFormEditor shouldn't be used on IE. Hide loading spinner and do not initialize preview.
        var spnrId = container.getAttribute("spnrId").replace(/:/g, "\\:");
        document.querySelector("#" + spnrId).style.display = 'none';

        if (isIE()) {
            // IE doesn't support document viewer & editor, show placeholder instead
            if (container) {
                container.style.height = "300px";
                container.style.backgroundColor = "var(--p-content-hover-background)";
                container.style.paddingTop = "140px";
                container.style.textAlign = "center";
                container.style.fontSize = "large";
                container.innerHTML = "Internet Explorer does not support document preview.<br/> Please, use any modern browser, like Mozilla Firefox, Opera, Google Chrome, Safari, Microsoft Edge, etc.";
            }
        }

        return;
    }

    initRecoFormEditor(true);
}


/**
 * Checks passed field's values and confidence leve for each symbol and marks symbols with low confidence level with red color
 *
 * @param {integer} confThreshold
 * @param {Node} htmlElem
 * @param {Object} fieldData
 * @returns {undefined}
 */
function hightlightWrongSymbols(confThreshold, htmlElem, fieldData) {
    var str = htmlElem.innerHTML.trim();
    var newStr = "";
    var whitespaces = 0;

    // Loop through initial text. Lead recognition char array doesn't contain whitespaces, so they must be ignored.
    // If current character's confidence level is lower, than ConfidenceThreshold for field - mark it as wrong
    for (var i = 0; i < str.length; i++) {
        if (/\s/.test(str.charAt(i))) {
            whitespaces++;
            newStr = newStr.concat(str.charAt(i));
        } else if (i - whitespaces >= fieldData.length || fieldData[i - whitespaces] >= confThreshold) {
            newStr = newStr.concat(str.charAt(i));
        } else {
            newStr = newStr.concat("<font color='red'>" + str.charAt(i) + "</font>");
        }
    }

    // Update field editor's text and make it editable
    htmlElem.innerHTML = newStr;
}


/**
 * Setup initial data and events for RecoForm fields
 *
 * @param {Node} field container
 * @returns {undefined}
 */
function initViewerTextField(field) {

    let pageIdx = field.dataset.pageId;
    let confThreshold = field.dataset.confidenceThreshold;

    let fieldContainer = field.querySelector("[id$='" + recoFieldSelector + "']");
    let fieldInputContainer = field.querySelector("[id$='" + recoFieldInputSelector + "']");
    let fieldCoordsContainer = field.querySelector("[id$='" + recoFieldCoordsSelector + "']");

    // Get symbols info for field
    let focusEnabled = field.dataset.focusEnabled === 'true';
    if (fieldCoordsContainer) {
        let symbolsInfo = JSON.parse(fieldCoordsContainer.value);
        if (fieldContainer.contentEditable === "true") {
            // Get HTML Element, that contains string to be displayed on UI
            hightlightWrongSymbols(confThreshold, fieldContainer, symbolsInfo);
        }
    }

    // Disable field focusing logic on Internet Explorer
    focusEnabled = focusEnabled && !isIE();

    if (focusEnabled) {
        // When field gets focused - we need to position preview on it and fix it while editing
        fieldContainer.addEventListener("click", () => recoFormEditor.selectField(parseInt(pageIdx), parseInt(field.dataset.fieldId)));
    }

    // When field loses focus - we need to update hidden fied with new content to pass it to backend
    fieldContainer.addEventListener("blur", () => {
        fieldInputContainer.value = fieldContainer.textContent || fieldContainer.innerText || "";
        // Call 'change' event on updated fields input
        changeEvent = new CustomEvent('change');
        fieldInputContainer.dispatchEvent(changeEvent);
    });

    // Typing in field should always print 'black', i.e. correct text, regardless of if we replacing wrong characters or not.
    // Should be done onkeydown, otherwise printing text next to (or in replace of) bad characters still gets marked 'bad'.
    fieldContainer.addEventListener("keydown", () => document.execCommand('foreColor', false, 'black'));
}

/**
 * Setup initial data and events for RecoForm table field cells
 *
 * @param {Node} tableField container
 * @returns {undefined}
 */
function initViewerTableField(tableField) {

    // Iterate over all rows of the table
    tableField.querySelectorAll("tr.recoTableRow").forEach(row => {
        // Iterate over all cells of the row
        row.querySelectorAll("div.recoTableCellField").forEach(cell => initViewerTableCell(cell));
    });
    if (!tableField.dataset.fieldInitialized) {
        let tableObserver = new MutationObserver((mutationsList) => {
            mutationsList
                    .filter(mutation => mutation.type === 'childList')
                    .filter(mutation => mutation.addedNodes)
                    .filter(mutation => mutation.addedNodes.length)
                    .flatMap(mutation => Array.from(mutation.addedNodes))
                    .filter(node => node.classList.contains('recoTableRow'))
                    .forEach(node => node.querySelectorAll("div.recoTableCellField").forEach(cell => initViewerTableCell(cell)));
        });
        tableObserver.observe(tableField.querySelectorAll("tbody")[0], {childList: true});
    }
}

function initViewerTableCell(cell) {
    let confThreshold = cell.dataset.confidenceThreshold;

    let cellContainer = cell.querySelector("[id$='" + recoFieldSelector + "']");
    let cellInputContainer = cell.querySelector("[id$='" + recoFieldInputSelector + "']");
    let cellCoordsContainer = cell.querySelector("[id$='" + recoFieldCoordsSelector + "']");

    if (cellContainer && cellContainer.contentEditable === "true") {
        // Get symbols info for field
        var symbolsInfo = JSON.parse(cellCoordsContainer.value);
        if (symbolsInfo) {
            // Get HTML Element, that contains string to be displayed on UI
            hightlightWrongSymbols(confThreshold, cellContainer, symbolsInfo);
        }
    }

    // When field loses focus - we need to update hidden field with new content to pass it to backend
    if (cellContainer) {
        cellContainer.addEventListener("blur", function () {
            cellInputContainer.value = cellContainer.textContent || cellContainer.innerText || "";
            // Call 'change' event on updated fields input
            changeEvent = new CustomEvent('change');
            cellInputContainer.dispatchEvent(changeEvent);
        });
    }
}


/**
 * Handles text-only preview of the current page on RecoWorkflow UI
 */
function drawTextPreview() {
    let textPreviewContainer = document.querySelector("#pageTextPreview");
    if (!textPreviewContainer) {
        return;
    }

    if (textPreviewContainer.dataset.textPreviewInitialized) {
        // Preview was already initialized
        return;
    }
    textPreviewContainer.dataset.textPreviewInitialized = true;

    let address = CONTEXT_PATH + "/api/recoDoc/" + textPreviewContainer.dataset.id + "/text/" + textPreviewContainer.dataset.pageIdx;

    fetch(address)
            .then(response => {
                if (response.status === 200) {
                    return response.json();
                } else {
                    return {};
                }
            })
            .then(responseJson => {
                if (responseJson.dims && responseJson.text) {
                    if (responseJson.text.length === 0) {
                        textPreviewContainer.innerHTML = '<div class="warning" style="padding: 50px 10px; text-align: center;">' + textPreviewContainer.dataset.notextMsg + '</div>'; // Removes loading animations
                    } else {
                        let svgNode = generateSvg(responseJson.dims, responseJson.text);
                        textPreviewContainer.innerHTML = ''; // Removes loading animations
                        textPreviewContainer.appendChild(svgNode);
                    }
                }
            })
            .catch((error) => console.error(error));
}


/**
 * Generates text-only preview SVG
 *
 * @param {type} dims
 * @param {type} text
 * @returns {type} generated SVG node
 */
function generateSvg(dims, text) {
    let textElement = undefined;
    let prevX = 0;
    let prevY = 0;

    // Create SVG element
    let svgNode = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svgNode.setAttribute("viewBox", "" + dims.x + " " + dims.y + " " + dims.w + " " + dims.h);

    // Process page text and add it to SVG
    text.forEach((symb) => {
        if (textElement === undefined) {
            // No text block was yet initialized
            textElement = document.createElementNS("http://www.w3.org/2000/svg", "text");
            textElement.setAttribute("x", symb.region.x);
            textElement.setAttribute("y", symb.region.y);
        }
        if (symb.region.y > prevY && symb.region.x < prevX) {
            // To big distance to next char - most probably this is new line
            svgNode.appendChild(textElement);
            textElement = document.createElementNS("http://www.w3.org/2000/svg", "text");
            textElement.setAttribute("x", symb.region.x);
            textElement.setAttribute("y", symb.region.y);
        }
        let text = document.createTextNode(symb.char);
        text = document.createElementNS("http://www.w3.org/2000/svg", "tspan");
        text.setAttribute("x", symb.region.x);
        text.setAttribute("y", symb.region.y);
        text.setAttribute("font-size", symb.fontSize);
        text.textContent = symb.char;
        textElement.appendChild(text);
        prevX = symb.region.x;
        prevY = symb.region.y + symb.region.h / 2;
    });
    svgNode.appendChild(textElement);

    // Add ability to scale svg using mouse wheel
    svgNode.addEventListener("wheel", (e) => {
        if (!e.ctrlKey) {
            // Scroll  available only on Ctrl+mouse wheel
            if (!svgNode.tooltip) {
                // Show tooltip only if it was not yet displayed
                let tooltip = document.createElement("div");
                tooltip.innerHTML = svgNode.parentNode.dataset.scrollTooltip;
                tooltip.className = "preview-scroll-tooltip";
                svgNode.tooltip = tooltip;
                svgNode.parentNode.append(tooltip);
                setTimeout(() => {
                    // Hide tooltip with effect
                    tooltip.style.opacity = 0;
                    setTimeout(() => {
                        // Remove tooltip
                        tooltip.parentNode.removeChild(tooltip);
                        svgNode.tooltip = undefined;
                    }, 800);
                }, 1500);
            }
            return;
        }

        e.preventDefault();
        let w = svgNode.viewBox.animVal.width;
        let h = svgNode.viewBox.animVal.height;
        let mx = e.offsetX;//mouse x
        let my = e.offsetY;
        let dw = w * Math.sign(e.deltaY) * 0.3;
        let dh = h * Math.sign(e.deltaY) * 0.3;
        let dx = dw * mx / svgNode.clientWidth;
        let dy = dh * my / svgNode.clientHeight;
        let viewBox = {
            x: svgNode.viewBox.animVal.x - dx,
            y: svgNode.viewBox.animVal.y - dy,
            w: svgNode.viewBox.animVal.width + dw,
            h: svgNode.viewBox.animVal.height + dh};
        svgNode.setAttribute('viewBox', `${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`);
    });

    // Define drag event listeners
    let dragStartListener = (e) => {
        if (e.target === svgNode) {
            // Start dragging, only if mousedown event registered on SVG image itself - this allows to keep
            // text selection events as is. PreventDefault will remove default behaviour of resetting text
            // selection on mouse click outside of text
            svgNode.dragging = true;
        }
    };
    let dragEndListener = (e) => {
        if (svgNode.dragging) {
            svgNode.dragging = false;
        }
    };
    let dragMoveListener = (e) => {
        if (svgNode.dragging) {
            e.preventDefault();
            // We need to adjust mouse pointer changes to original scale, applied to fit SVG to container
            let dx = e.movementX / (svgNode.clientWidth / svgNode.viewBox.animVal.width);
            let dy = e.movementY / (svgNode.clientWidth / svgNode.viewBox.animVal.width);
            // Apply changes to viewbox
            let viewBox = {
                x: svgNode.viewBox.animVal.x - dx,
                y: svgNode.viewBox.animVal.y - dy,
                w: svgNode.viewBox.animVal.width,
                h: svgNode.viewBox.animVal.height};
            svgNode.setAttribute('viewBox', `${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`);
        }
    };
    // Add listeners for dragging SVG by mouse
    svgNode.addEventListener("mousedown", dragStartListener);
    window.addEventListener("mouseup", dragEndListener);
    window.addEventListener("mousemove", dragMoveListener);

    return svgNode;
}


if (document.readyState !== 'loading') {
    // in case the document is already rendered
    initRecoDocPreview();
    drawTextPreview();
} else if (document.addEventListener) {
    // modern browsers
    document.addEventListener('DOMContentLoaded', initRecoDocPreview);
    document.addEventListener('DOMContentLoaded', drawTextPreview);
} else {
    // IE <= 8
    document.attachEvent('onreadystatechange', function () {
        if (document.readyState === 'complete') {
            initRecoDocPreview();
            drawTextPreview();
        }
    });
}

// Add MutationObserver to handle RecoForm change - in this case old inputs removed and we need to re-init
// all existing field editors
var observer = new MutationObserver(initRecoDocViewer);
var textPreviewObserver = new MutationObserver(drawTextPreview);

// define what element should be observed by the observer
// and what types of mutations trigger the callback
observer.observe(document, {
    subtree: true,
    childList: true,
    attributes: false,
    characterData: false
});
textPreviewObserver.observe(document, {
    subtree: true,
    childList: true,
    attributes: false,
    characterData: false
});
