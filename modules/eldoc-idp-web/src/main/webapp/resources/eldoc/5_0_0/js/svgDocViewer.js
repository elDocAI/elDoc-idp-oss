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

/* global PF */

class SvgDocViewer extends EventTarget {
    static #ELEMENT_ID_PREVIEW_CONTAINER = 'f1:previewArea';
    static #WV_ID_PREVIEW_CONTAINER = 'pagePaginatorWV';
    static #ELEMENT_ID_PREVIEW_IMAGE_CONTAINER = 'f1:previewImageArea';

    //
    // Viewer state
    //
    #initialized = false;
    #previewMutationObserver;
    #postInitCallbacks = [];

    //
    // Main config
    //
    #containerId;
    #readOnly;
    #scaleBy = 0.25;
    #previewApiURL;
    #previewId;
    #pagesCount;
    #pages = {};
    #pageIdx;
    #allowPageRotation;
    #allowPageDisabling;

    //
    // External structure
    //
    #parentEL;
    #svgEL;

    //
    // Internal structure
    //
    #mainArea;
    #pageListArea;
    #pageImage;

    #previewMutationHandler() {
        if (!this.#initialized) {
            // RecoFormEditor is already being initialized - nothing to do
            return;
        }
        this.#initialized = false;
        if (this.#readOnly) {
            this.clearSelection();
        }
        if (this.#parentEL.contains(this.#svgEL)) {
            this.#parentEL.removeChild(this.#svgEL);
        }
        this.init();
    }

    constructor(container = "pdfViewer", readonly = false) {
        super();
        if (typeof container === 'String') {
            this.#parentEL = document.getElementById(this.#containerId);
        } else {
            this.#parentEL = container;
        }
        this.#readOnly = readonly;
        if (!this.#readOnly) {
            this.#scaleBy = 0.05;
        }

        this.refreshObservers();
    }

    /**
     * Builds initial editor view, including fields
     *
     * @returns {undefined}
     */
    init() {
        this.#initialized = false;

        if (!this.#parentEL) {
            // Cannot find PdfViewer area;
            return;
        }

        let childIds = [this.#parentEL.id, SvgDocViewer.#ELEMENT_ID_PREVIEW_CONTAINER, 'f1'];
        let previewPaginatorWidget = PrimeFaces.widgets[SvgDocViewer.#WV_ID_PREVIEW_CONTAINER];
        if (previewPaginatorWidget) {
            childIds.push(previewPaginatorWidget.id);
        }
        this.#previewMutationObserver = createMutationObserver(document, childIds, () => this.#previewMutationHandler());
        this.#pagesCount = this.#parentEL.getAttribute('pageCount');
        this.#pageIdx = 0;
        this.#previewApiURL = this.#parentEL.getAttribute('previewApiUrl');
        this.#previewId = this.#parentEL.getAttribute('previewId');

        this.#mainArea = this.#parentEL.querySelector('[id$="pageContent"]');
        this.#pageListArea = this.#parentEL.querySelector('[id$="pagesList"]');

        this.#svgEL = createSvgElement("svg");
        this.#svgEL.setAttribute('visibility', 'hidden');
        this.#mainArea.appendChild(this.#svgEL);
        this.#pageImage = createSvgElement('image');
        this.#svgEL.appendChild(this.#pageImage);

        this.#pageImage.setAttribute('href', this.#getPageImageUrl(this.#pageIdx));

        return new Promise((resolve, reject) => {
            this.#pageImage.onload = async () => {
                this.#svgEL.setAttribute('viewBox', '0 0 ' + this.#svgEL.getBBox().width + ' ' + this.#svgEL.getBBox().height);
                this.#pageListArea.style.height = this.#mainArea.clientHeight + 'px';
//            this.#initEditorContent();
                this.#svgEL.removeAttribute('visibility');
                while (this.#postInitCallbacks.length > 0) {
                    let callback = this.#postInitCallbacks.shift();
                    callback();
                }
                this.#loadPagesPreview().then(pagePreviews => {
                    this.#pages = pagePreviews;
                    pagePreviews.forEach(page => this.#pageListArea.appendChild(page.divEL));
                    this.#pageImage.onload = undefined;
                    this.#initialized = true;
                    resolve();
                });
            };
        });
    }

    #getPageImageUrl(pageIdx) {
        return this.#previewApiURL + '?id=' + this.#previewId + '&p=' + pageIdx + '&keepItem=true';
    }

    #loadPagesPreview() {
        let pagePreviewPromises = [];
        for (let pageIdx = 0; pageIdx < this.#pagesCount; pageIdx++) {
            pagePreviewPromises.push(this.#createPagePreview(pageIdx));
        }
        return Promise.all(pagePreviewPromises);
    }

    #createPagePreview(pageIdx) {
        let pagePreviewImg = document.createElement('img');
        pagePreviewImg.src = this.#getPageImageUrl(pageIdx);
        return new Promise((resolve, reject) => {
            pagePreviewImg.onload = () => {
                let page = {
                    pageIdx: pageIdx,
                    rotation: 0,
                    disabled: false
                };

                page.divEL = document.createElement('div');
                page.divEL.style.display = 'flex';
                page.divEL.style.flexWrap = 'wrap';
                page.divEL.style.justifyContent = 'center';
                page.divEL.style.cursor = 'pointer';
                page.divEL.style.position = 'relative';

                let pagePreviewDiv = document.createElement('div');
                page.divEL.appendChild(pagePreviewDiv);
                pagePreviewDiv.style.position = 'relative';
                pagePreviewDiv.style.width = '100%';
                pagePreviewDiv.style.textAlign = 'center';
                pagePreviewDiv.onclick = () => this.#setPage(pageIdx);
                pagePreviewImg.id = 'img';
                page.img = pagePreviewImg;
                pagePreviewDiv.appendChild(pagePreviewImg);
                pagePreviewImg.style.maxWidth = '75%';
                pagePreviewImg.style.border = '1px solid lightgrey';
                pagePreviewImg.style.borderRadius = '5px';

                if (this.#allowPageRotation || this.#allowPageDisabling) {
                    let pageControlsDiv = document.createElement('div');
                    page.divEL.appendChild(pageControlsDiv);
                    pageControlsDiv.style.position = 'relative';
                    pageControlsDiv.style.top = '-30px';
                    pageControlsDiv.style.fontSize = '20px';
                    if (this.#allowPageRotation) {
                        let rotateBtn = document.createElement('i');
                        rotateBtn.style.backgroundColor = 'lightgray';
                        rotateBtn.style.border = '1px solid lightgrey';
                        rotateBtn.style.borderRadius = '5px';
                        rotateBtn.style.opacity = '0.6';
                        rotateBtn.className = 'fa-solid fa-fw fa-rotate-right';
                        rotateBtn.onmouseover = () => rotateBtn.style.opacity = '1';
                        rotateBtn.onmouseout = () => rotateBtn.style.opacity = '0.6';
                        rotateBtn.onclick = (e) => this.#pageRotationHandler(e, page);
                        pageControlsDiv.appendChild(rotateBtn);
                    }
                    if (this.#allowPageDisabling) {
                        let blockBtn = document.createElement('i');
                        blockBtn.style.backgroundColor = 'lightgray';
                        blockBtn.style.border = '1px solid lightgrey';
                        blockBtn.style.borderRadius = '5px';
                        blockBtn.style.opacity = '0.6';
                        blockBtn.className = 'fa-solid fa-fw fa-ban';
                        blockBtn.onmouseover = () => blockBtn.style.opacity = '1';
                        blockBtn.onmouseout = () => blockBtn.style.opacity = '0.6';
                        blockBtn.onclick = (e) => this.#pageDisablingEventListener(e, page);
                        pageControlsDiv.appendChild(blockBtn);
                    }
                }

                let pageIdxBlock = document.createElement('div');
                page.divEL.appendChild(pageIdxBlock);
                pageIdxBlock.style.position = 'relative';
                pageIdxBlock.style.width = '100%';
                pageIdxBlock.style.textAlign = 'center';
                pageIdxBlock.innerHTML = pageIdx + 1;
                pageIdxBlock.style.top = '-25px';
                resolve(page);
            };
        });
    }

    #pageRotationHandler(e, page) {
        e.preventDefault();
        this.#setPageRotation(page, (page.rotation + 90) % 360);
        const event = new CustomEvent('pageRotated', {detail: {pageIdx: page.pageIdx, rotation: page.rotation}});
        this.dispatchEvent(event);
    }

    #pageDisablingEventListener(e, page) {
        e.preventDefault();
        this.#setPageDisabled(page, !page.disabled);
        const event = new CustomEvent('pageDisabled', {detail: {pageIdx: page.pageIdx, disabled: page.disabled}});
        this.dispatchEvent(event);
    }

    #setPage(pageIdx) {
        this.#pageIdx = pageIdx;
        let canvas = document.createElement('canvas');
        let width = this.#pages[pageIdx].rotation % 180 === 0 ? this.#pages[pageIdx].img.naturalWidth : this.#pages[pageIdx].img.naturalHeight;
        let height = this.#pages[pageIdx].rotation % 180 === 0 ? this.#pages[pageIdx].img.naturalHeight : this.#pages[pageIdx].img.naturalWidth;
        canvas.width = width;
        canvas.height = height;
        this.#svgEL.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
        let context = canvas.getContext('2d');
        if (this.#pages[pageIdx].rotation === 90) {
            context.translate(this.#pages[pageIdx].img.naturalHeight, 0);
        } else if (this.#pages[pageIdx].rotation === 180) {
            context.translate(this.#pages[pageIdx].img.naturalWidth, this.#pages[pageIdx].img.naturalHeight);
        } else if (this.#pages[pageIdx].rotation === 270) {
            context.translate(0, this.#pages[pageIdx].img.naturalWidth);
        }
        context.rotate(this.#pages[pageIdx].rotation / 180 * Math.PI);
        context.drawImage(this.#pages[pageIdx].img, 0, 0);
        this.#pageImage.setAttribute('href', canvas.toDataURL());
        const event = new CustomEvent('page', {detail: {pageIdx: this.#pageIdx}});
        this.dispatchEvent(event);
    }

    #setPageRotation(page, rotation) {
        if (!page.img.dataset.width || !page.img.dataset.height) {
            page.img.dataset.width = page.img.clientWidth;
            page.img.dataset.height = page.img.clientHeight;
        }
        page.rotation = rotation;
        page.img.style.rotate = '' + page.rotation + 'deg';
        let height = page.rotation % 180 === 0 ? (page.img.dataset.width) : (page.img.dataset.height);
        page.img.style.width = height + 'px';
    }

    #setPageDisabled(page, disabled) {
        if (!disabled) {
            page.disabled = false;
            page.divEL.style.opacity = '1';
        } else {
            page.disabled = true;
            page.divEL.style.opacity = '0.3';
        }
    }

    setPageRotation(pageIdx, rotation) {
        this.#setPageRotation(this.#pages[pageIdx], rotation);
    }

    setPageDisabled(pageIdx, disabled) {
        this.#setPageDisabled(this.#pages[pageIdx], disabled);
    }

    addPreviewOverlay(overlay) {
        this.#svgEL.appendChild(overlay);
        overlay.setAttribute('x', this.#pageImage.getBBox().x);
        overlay.setAttribute('y', this.#pageImage.getBBox().y);
        overlay.setAttribute('width', this.#pageImage.getBBox().width);
        overlay.setAttribute('height', this.#pageImage.getBBox().width);
    }

    isAllowPageRotation() {
        return this.#allowPageRotation;
    }

    setAllowPageRotation(allowRotation) {
        this.#allowPageRotation = allowRotation;
    }

    isAllowPageDisabling() {
        return this.#allowPageDisabling;
    }

    setAllowPageDisabling(allowDisabling) {
        this.#allowPageDisabling = allowDisabling;
    }

    /**
     * Append mutation observer to editor root element to properly bind to inputs state
     * Should be removed/replaced after switching to REST communication and editor UI state control via JS
     *
     * @returns {undefined}
     */
    refreshObservers() {
    }
}

function createSvgElement(name) {
    return document.createElementNS("http://www.w3.org/2000/svg", name);
}

function createMutationObserver(monitoredNodeId, childIds, handler) {
    let observer = new MutationObserver((mutationsList) => {
        mutationsList
                .filter(mutation => mutation.type === 'childList')
                .filter(mutation => mutation.addedNodes)
                .filter(mutation => mutation.addedNodes.length)
                .flatMap(mutation => Array.from(mutation.addedNodes))
                .filter(node => childIds.includes(node.id))
                .forEach((node) => handler(node));
    });
    if (typeof monitoredNodeId === 'string') {
        observer.observe(document.getElementById(monitoredNodeId), {subtree: true, childList: true});
    } else {
        observer.observe(monitoredNodeId, {subtree: true, childList: true});
    }
}
