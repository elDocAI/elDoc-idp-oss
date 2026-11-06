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
/* global Rectangle */

function getPrimeFacesWidgetIfAvailable(widgetVar) {
    return PrimeFaces.widgets[widgetVar] ?? null;
}

class Field extends EventTarget {

    static #OPACITY_SEARCH_AREA = 0.2;
    static #OPACITY_LABEL_UNDER_TABLE = 0.6;

    static TYPE = {
        'TEXT': 'TEXT',
        'OMR': 'OMR',
        'OPMARK': 'OPMARK',
        'BARCODE': 'BARCODE',
        'IMAGE': 'IMAGE',
        'TABLE': 'TABLE',
        'COLUMN': 'COLUMN',
        'TEXT_EXTENDABLE': 'TEXT_EXTENDABLE'
    }
    static #COLORS = {
        'TEXT': 'red',
        'OMR': 'orange',
        'OPMARK': 'darkorange',
        'BARCODE': 'yellow',
        'IMAGE': 'green',
        'TABLE': 'blue',
        'COLUMN': 'darkblue',
        'TEXT_EXTENDABLE': 'purple',
        'LABEL': 'olive',
        'LABEL_UNDER_TABLE': 'olive',
        'LABEL_SEARCH_RECT': 'gray',
        'HEADER': 'lightgray',
        'FOOTER': 'lightgray'
    };

    #layer;

    //
    // Field coordinates on page
    //
    #parent;
    #pageId = 0;
    #anchorUnderTablePageId = 0;
    #fieldId = 0;
    #rowId = -1;
    #colId = -1;

    //
    // Field layout
    //
    #label;
    #valueRect;
    #childList = [];
    #childListGroup;
    #bottomLabel;
    #labelSearchArea;
    #showField = true;
    #showAnchorUnderTable = true;
    #showValueRect = true;

    //
    // Backup state to be able to properly cancel edits, if needed
    //
    #valueRectBackup;
    #labelBackup;
    #bottomLabelBackup;

    #type;
    #scale;
    #hidden = false;

    /**
     *
     * @param {SVG.Group} layer
     * @param {Number} pageId Current page index
     * @param {Number} fieldId
     * @param {Number} rowId
     * @param {Number} colId
     * @param {Field.TYPE} type
     * @param {Number} initialFieldsScale
     * @param {Object} fieldData
     * @param {Field} parent
     * @returns {Field}
     */
    constructor(layer, pageId, fieldId, rowId, colId, type, initialFieldsScale = 1.0, fieldData, parent = undefined) {
        super();
        this.#layer = layer;
        this.#pageId = fieldData.pageId;
        this.#fieldId = fieldId;
        this.#rowId = rowId;
        this.#colId = colId;
        this.#type = type;
        this.#scale = initialFieldsScale;
        this.#parent = parent;

        this.loadFieldData(fieldData, pageId);
    }

    loadFieldData(fieldData, pageId) {
        if (fieldData.label) {
            this.#createLabelRect(fieldData.label);
        }
        if (fieldData.coords) {
            this.#createValueRect(fieldData.coords);
            if (fieldData.isIgnored) {
                this.#valueRect.hide();
            }
        }
        if (fieldData.anchorUnderTable) {
            this.#createBottomLabelRect(fieldData.anchorUnderTable);
        }

        if (this.#type === Field.TYPE.TABLE || this.#type === Field.TYPE.OMR) {
            // This field has 'child' content - subfields. Init proper UI container
            if (this.#childListGroup) {
                this.#layer.removeChild(this.#childListGroup);
            }
            this.#childListGroup = createSvgElement('g');
            this.#childListGroup.setAttribute('x', this.#valueRect.getX());
            this.#childListGroup.setAttribute('y', this.#valueRect.getY());
            this.#childListGroup.setAttribute('width', this.#valueRect.getWidth());
            this.#childListGroup.setAttribute('height', this.#valueRect.getHeight());
            this.#layer.prepend(this.#childListGroup);
        }

        if (this.#childList.length !== 0) {
            this.#childList.length = 0;
        }

        if (this.#type === Field.TYPE.TABLE) {
            // Process table content
            if (typeof fieldData.anchorUnderTable === 'undefined') {
                // This is RecoWorkflow viewer - for RecoForm/RecoField editor, fieldData contains anchor under table, even if it is disabled
                this.#valueRect.hide();
                this.#showValueRect = false;
            }
            fieldData.cols.forEach((child, childIdx) => {
                if (Array.isArray(child)) {
                    let row = [];
                    child.forEach((cellData, cellIdx) => {
                        let cell = new Field(this.#childListGroup, this.#pageId, this.#fieldId, childIdx, cellIdx, Field.TYPE.COLUMN, this.#scale, cellData, this);
                        if (cell.#valueRect) {
                            cell.#valueRect.setDraggable(true, false, false);
                        }
                        row.push(cell);
                    });
                    this.#childList.push(row);
                } else {
                    if (this.#childList.length === 0) {
                        this.#childList.push([]);
                    }
                    if (!child.coords) {
                        child.coords = child;
                    }
                    // We need to adjust cell coordinates, as in this case they are relative to table TL corner, and not global
                    child.coords.x = child.coords.x + fieldData.coords.x;
                    child.coords.y = child.coords.y + fieldData.coords.y;
                    let cell = new Field(this.#childListGroup, this.#pageId, this.#fieldId, 0, childIdx, Field.TYPE.COLUMN, this.#scale, child, this);
                    if (cell.#valueRect) {
                        cell.#valueRect.setDraggable(true, false, false);
                    }
                    if (child.ignored) {
                        cell.hide();
                    }
                    this.#childList[0].push(cell);
                }
            });

            if (this.#bottomLabel) {
                this.#anchorUnderTablePageId = fieldData.anchorUnderTablePageId;
                let anchorUnderTableOnSamePageAsField = this.#anchorUnderTablePageId === 0;
                let fieldOnCurrentPage = fieldData.pageId === pageId;

                let anchorUnderTablePageIdDefined = this.#anchorUnderTablePageId !== 0;
                let anchorUnderTableDefinedOnCurrentPage = anchorUnderTablePageIdDefined && this.#anchorUnderTablePageId === pageId;

                this.#showAnchorUnderTable = (fieldOnCurrentPage && anchorUnderTableOnSamePageAsField) || anchorUnderTableDefinedOnCurrentPage;
                if (!this.#showAnchorUnderTable) {
                    this.#bottomLabel.hide();
                }
            }
        } else if (this.#type === Field.TYPE.OMR) {
            this.#childList.push([]);
            fieldData.opMarks.forEach((opMark, childIdx) => {
                if (!opMark.coords) {
                    opMark.coords = opMark;
                }
                // We need to adjust cell coordinates, as in this case they are relative to table TL corner, and not global
                opMark.coords.x = opMark.coords.x + fieldData.coords.x;
                opMark.coords.y = opMark.coords.y + fieldData.coords.y;
                let omrRect = new Field(this.#childListGroup, this.#pageId, this.#fieldId, 0, childIdx, Field.TYPE.OPMARK, this.#scale, opMark, this);
                omrRect.setDraggable(false);
                this.#childList[0].push(omrRect);
            });
        }

        if (this.#pageId !== pageId) {
            this.#showField = false;
            // This is possible, if only part of the field is on current page - e.g. bottom anchor
            // In this case all other content should stay hidden
            if (this.#label) {
                this.#label.hide();
            }
            if (this.#valueRect) {
                this.#valueRect.hide();
            }
            if (this.#childListGroup) {
                this.#childListGroup.setAttribute('visibility', 'hidden');
            }
        }

        this.#scaleField(this.#scale);

        this.#attachEventListeners();
        this.#attachEventDispatchers();
    }

    /**
     * Adds SearchArea rect
     *
     * @param {Number} extTop
     * @param {Number} extRight
     * @param {Number} extBottom
     * @param {Number} extLeft
     * @returns {undefined}
     */
    drawSearchArea(extTop, extRight, extBottom, extLeft) {
        this.hideSearchArea();

        // Calculate anchorSearchArea
        let x = this.#label.getX() - this.#label.getWidth() * extLeft / 100;
        let y = this.#label.getY() - this.#label.getHeight() * extTop / 100;
        let w = this.#label.getWidth() + this.#label.getWidth() * (extLeft + extRight) / 100;
        let h = this.#label.getHeight() + this.#label.getHeight() * (extTop + extBottom) / 100;

        this.#labelSearchArea = new Rectangle(this.#layer, x, y, w, h);
        this.#labelSearchArea.setFill(Field.#COLORS['LABEL']);
        this.#labelSearchArea.setOpacity(Field.#OPACITY_SEARCH_AREA);
        this.#labelSearchArea.setDraggable(false);
        this.#labelSearchArea.moveToBottom();
    }

    /**
     * Removes search area for this field
     *
     * @returns {undefined}
     */
    hideSearchArea() {
        if (!this.#labelSearchArea) {
            return;
        }
        this.#labelSearchArea.destroy();
        this.#labelSearchArea = undefined;
    }

    encodeAsObject(scale = 1.0, force = false) {
        if (!force && this.#type === Field.TYPE.COLUMN) {
            return this.#parent.encodeAsObject(scale);
        }
        let fieldData = {};
        fieldData.coords = this.#valueRect.encodeAsObject(scale);
        if (this.#label) {
            fieldData.label = this.#label.encodeAsObject(scale);
        }
        if (this.#type === Field.TYPE.TABLE) {
            fieldData.cols = this.#childList.flat().map(r => r.#valueRect.encodeAsObject(scale));
            fieldData.cols.forEach(col => col.x = col.x - this.#valueRect.getX() * scale);
            fieldData.cols.forEach(col => col.y = col.y - this.#valueRect.getY() * scale);
        }
        if (this.#type === Field.TYPE.OMR) {
            fieldData.opMarks = this.#childList.flat().map(r => r.#valueRect.encodeAsObject(scale));
            fieldData.opMarks.forEach(col => col.x = col.x - this.#valueRect.getX() * scale);
            fieldData.opMarks.forEach(col => col.y = col.y - this.#valueRect.getY() * scale);
        }
        if (this.#bottomLabel) {
            fieldData.anchorUnderTable = this.#bottomLabel.encodeAsObject(scale);
        }
        return fieldData;
    }

    encodeAsString(scale = 1.0) {
        if (this.#type === Field.TYPE.IMAGE || this.#type === Field.TYPE.COLUMN) {
            return this.#valueRect.encodeAsString(scale);
        }
        return undefined;
    }

    setBottomLabelDisabled(disabled) {
        if (!this.#bottomLabel && !disabled) {
            this.#createBottomLabelRect({x: this.#label.getX(), y: this.#valueRect.getY() + this.#valueRect.getHeight(), w: this.#label.getWidth(), h: this.#label.getHeight()});
            this.#attachEventDispatchersBottomLabel();
            this.#bottomLabel.setDraggable(true);
            this.#bottomLabel.setResizable(true);
            this.#bottomLabel.select();
        }
        if (disabled) {
            this.#bottomLabel.hide();
        } else {
            this.#bottomLabel.show();
        }
    }

    initFieldPropListeners() {

    }

    #createLabelRect(coords) {
        if (this.#type === Field.TYPE.COLUMN || this.#type === Field.TYPE.OPMARK) {
            // Do not create label for columns and opMarks
            return;
        }
        if (this.#label) {
            this.#label.destroy();
        }
        this.#label = new Rectangle(this.#layer, coords.x, coords.y, coords.w, coords.h);
        this.#label.setFill(Field.#COLORS['LABEL']);
    }

    #createValueRect(coords) {
        if (this.#valueRect) {
            this.#valueRect.destroy();
        }
        this.#valueRect = new Rectangle(this.#layer, coords.x, coords.y, coords.w, coords.h, !recoFormEditor.isReadOnly() && recoFormEditor.isEditMode());
        this.#valueRect.setFill(Field.#COLORS[this.#type]);
        if (this.#type === Field.TYPE.TABLE) {
            if (recoFormEditor.isReadOnly()) {
                this.#valueRect.setDraggable(false);
            }
            this.#valueRect.setBoundBoxFunc((oldBox, newBox) => this.#tableBoundBox(oldBox, newBox));
        } else if (this.#type === Field.TYPE.COLUMN && this.#parent.getFieldValueRect().isVisible()) {
            this.#valueRect.setBoundBoxFunc((oldBox, newBox) => this.#columnBoundBox(oldBox, newBox));
        }
    }

    /**
     * BoundBox function for Tables, that sets restrictions and additional rules, used on table resizing, including:
     * 1) First/Last column of the table should be resized alongside the table;
     * 2) Changing table's height should change all column's height;
     * 3) Column's can't be less than 10px wide;
     *
     * @param {type} oldBox
     * @param {type} newBox
     * @returns {unresolved}
     */
    #tableBoundBox(oldBox, newBox) {
        let diffX = newBox.x - oldBox.x;
        let diffW = newBox.w - oldBox.w;

        if (this.#childList.length !== 0 && this.#childList[0].length !== 0) {
            // If resize will cause first column to be to small - stop resizing;
            let firstCol = this.#childList[0][0].#valueRect;
            if ((firstCol.getWidth() + diffW) <= 10) {
                return oldBox;
            }

            // If resizing will cause last column to be to small - stop resizing;
            let lastCol = this.#childList[0][this.#childList[0].length - 1].#valueRect;
            if (this.#childList[0].length > 1 && newBox.x === oldBox.x) {
                if ((lastCol.getWidth() + diffW) <= 10) {
                    return oldBox;
                }
            }

            if (newBox.x !== oldBox.x) {
                // X position changed - meaning we are resizing left side of table
                firstCol.setWidth(firstCol.getWidth() - diffX);
                firstCol.setX(firstCol.getX() + diffX);
            } else if (newBox.w !== oldBox.w) {
                // X position same, but width changed - right side of table being resized
                lastCol.setWidth(lastCol.getWidth() + diffW);
            }
            this.#childList[0].forEach(col => col.#valueRect.setY(newBox.y));
            this.#childList[0].forEach(col => col.#valueRect.setHeight(newBox.h));
        }
        this.#childListGroup.setAttribute('x', newBox.x);
        this.#childListGroup.setAttribute('y', newBox.y);
        this.#childListGroup.setAttribute('width', newBox.w);
        this.#childListGroup.setAttribute('height', newBox.h);
        return newBox;
    }

    /**
     * BoundBox function for Columns, that sets restrictions and additional rules used on columns resizing, including:
     * 1) Column's can't be less than 10px wide;
     * 2) Resizing columns should move/resize adjuscent columns;
     * 3) Can't move left border of first field or right border of last field - this should cause table's size change, so should be done via table resizing;
     *
     * @param {type} oldBox
     * @param {type} newBox
     * @returns {unresolved}
     */
    #columnBoundBox(oldBox, newBox) {
        let prevCol = this.#colId === 0 ? undefined : this.#parent.#childList[0][this.#colId - 1].#valueRect;
        let nextCol = this.#colId === this.#parent.#childList[0].length - 1 ? undefined : this.#parent.#childList[0][this.#colId + 1].#valueRect;
        if (newBox.w < 10) {
            return oldBox;
        }
        if (Math.abs(newBox.x - oldBox.x) > 0.01) {
            if (prevCol) {
                let diff = newBox.x - oldBox.x;
                let prevColNewWidth = prevCol.getWidth() + diff;
                if (prevColNewWidth > 10) {
                    prevCol.setWidth(prevColNewWidth);
                } else {
                    return oldBox;
                }
            } else {
                return oldBox;
            }
        } else if (newBox.w !== oldBox.w) {
            if (nextCol) {
                let diff = newBox.w - oldBox.w;
                let nextColNewWidth = nextCol.getWidth() - diff;
                if (nextColNewWidth > 10) {
                    nextCol.setX(nextCol.getX() + diff);
                    nextCol.setWidth(nextColNewWidth);
                } else {
                    return oldBox;
                }
            } else if (newBox.x === oldBox.x) {
                return oldBox;
            }
        }
        return newBox;
    }

    #createBottomLabelRect(coords) {
        if (this.#type === Field.TYPE.COLUMN) {
            // Do not create label for columns
            return;
        }
        if (this.#bottomLabel) {
            this.#bottomLabel.destroy();
        }
        this.#bottomLabel = new Rectangle(this.#layer, coords.x, coords.y, coords.w, coords.h);
        this.#bottomLabel.setFill(Field.#COLORS['LABEL_UNDER_TABLE']);
        this.#bottomLabel.setOpacity(Field.#OPACITY_LABEL_UNDER_TABLE);
    }

    #scaleField(scale) {
        if (this.#label) {
            this.#label.scale(scale);
        }
        if (this.#valueRect) {
            this.#valueRect.scale(scale);
        }
        if (this.#bottomLabel) {
            this.#bottomLabel.scale(scale);
        }
        if (this.#childListGroup) {
            this.#childListGroup.setAttribute('x', this.#childListGroup.getAttribute('x') * scale);
            this.#childListGroup.setAttribute('y', this.#childListGroup.getAttribute('y') * scale);
            this.#childListGroup.setAttribute('width', this.#childListGroup.getAttribute('width') * scale);
            this.#childListGroup.setAttribute('height', this.#childListGroup.getAttribute('height') * scale);
        }
    }

    /**
     * Attaches event listeners, that listen to events, that transitivelly changes this field's state
     *
     * @returns {undefined}
     */
    #attachEventListeners() {
        switch (this.#type) {
            case Field.TYPE.OMR:
            case Field.TYPE.TABLE:
                this.#valueRect.addEventListener("rectDragMove", (e) => {
                    this.#childListGroup.setAttribute('x', e.target.getX());
                    this.#childListGroup.setAttribute('y', e.target.getY());
                    this.#childList.flat().forEach((child) => {
                        if (e?.detail?.dx) {
                            child.getFieldValueRect().setX(child.getFieldValueRect().getX() + e.detail.dx);
                        }
                        if (e?.detail?.dy) {
                            child.getFieldValueRect().setY(child.getFieldValueRect().getY() + e.detail.dy);
                        }
                    });
                });
                break;
        }
    }

    /**
     * Attaches event listeners, that will process and pass it to parent element
     *
     * @returns {undefined}
     */
    #attachEventDispatchers() {
        if (this.#valueRect) {
            this.#valueRect.addEventListener('rectTransformEnd', (e) => {
                const event = new CustomEvent('fieldTransformEnd', e);
                this.dispatchEvent(event);
            });
            this.#valueRect.addEventListener('rectClick', (e) => {
                const event = new CustomEvent('fieldClick', e);
                this.dispatchEvent(event);
            });
        }
        if (this.#label) {
            this.#label.addEventListener('rectTransformEnd', (e) => {
                const eventDragEnd = new CustomEvent('fieldTransformEnd', e);
                this.dispatchEvent(eventDragEnd);
                const event = new CustomEvent('fieldLabelTransformEnd', e);
                this.dispatchEvent(event);
            });
        }

        this.#attachEventDispatchersBottomLabel();
    }

    #attachEventDispatchersBottomLabel() {
        if (this.#bottomLabel) {
            this.#bottomLabel.addEventListener('rectTransformEnd', (e) => {
                const eventDragEnd = new CustomEvent('fieldTransformEnd', e);
                this.dispatchEvent(eventDragEnd);
                const event = new CustomEvent('fieldLabelTransformEnd', e);
                this.dispatchEvent(event);
            });
        }
    }

    show() {
        if (this.#valueRect && this.#showField && this.#showValueRect) {
            this.#valueRect.show();
        }
        if (this.#label && this.#showField) {
            this.#label.show();
        }
        if (this.#bottomLabel && this.#showAnchorUnderTable) {
            this.#bottomLabel.show();
        }
        if (this.#childListGroup && this.#showField) {
            this.#childListGroup.setAttribute('visibility', 'visible');
        }
        this.#hidden = false;
    }

    hide() {
        if (this.#valueRect) {
            this.#valueRect.hide();
        }
        if (this.#label) {
            this.#label.hide();
        }
        if (this.#bottomLabel) {
            this.#bottomLabel.hide();
        }
        if (this.#childListGroup) {
            this.#childListGroup.setAttribute('visibility', 'hidden');
        }
        this.#hidden = true;
    }

    select() {
        if (this.#valueRect) {
            this.#valueRect.setDraggable(recoFormEditor.isEditMode() && (!recoFormEditor.isReadOnly() || this.#type === Field.TYPE.IMAGE || this.#type === Field.TYPE.COLUMN));
            this.#valueRect.setResizable(recoFormEditor.isEditMode() && (!recoFormEditor.isReadOnly() || this.#type === Field.TYPE.IMAGE || this.#type === Field.TYPE.COLUMN));
            this.#valueRect.select(this.#type === Field.TYPE.COLUMN && this.#parent.getFieldValueRect().isVisible() ? [Transformer.ANCHORS.RIGHT, Transformer.ANCHORS.LEFT] : undefined);
        }
        if (this.#label) {
            this.#label.setDraggable(this.#label.isVisible());
            this.#label.setResizable(this.#label.isVisible());
            this.#label.select();
        }
        if (this.#bottomLabel) {
            this.#bottomLabel.setDraggable(this.#bottomLabel.isVisible());
            this.#bottomLabel.setResizable(this.#bottomLabel.isVisible());
            this.#bottomLabel.select();
        }
        if (this.#type === Field.TYPE.COLUMN) {
            this.#parent.#valueRect.moveToBottom();
        }
    }

    deselect() {
        if (this.#valueRect) {
            this.#valueRect.deselect();
        }
        if (this.#label) {
            this.#label.deselect();
            this.#label.setDraggable(false);
        }
        if (this.#bottomLabel) {
            this.#bottomLabel.deselect();
            this.#bottomLabel.setDraggable(false);
        }
        this.#childList.flat().forEach(cell => cell.deselect());
        if (this.#type === Field.TYPE.COLUMN) {
            this.#parent.#valueRect.moveToTop();
        }
    }

    destroy() {
        if (this.#valueRect) {
            this.#valueRect.destroy();
        }
        if (this.#label) {
            this.#label.deselect();
            this.#label.destroy();
        }
        if (this.#bottomLabel) {
            this.#bottomLabel.deselect();
            this.#bottomLabel.destroy();
        }
        this.#childList.flat().forEach(cell => cell.destroy());
    }

    addColumn() {
        if (!this.isTypeTable()) {
            return;
        }
        let sourceColEncoded;
        let targetFieldIdx;
        if (this.#childList.length === 0 || this.#childList[0].length === 0) {
            // There is no columns yet - create column for all field's width
            sourceColEncoded = this.encodeAsObject(1.0);
            targetFieldIdx = 0;
        } else {
            let lastCol = this.#childList[0][this.#childList[0].length - 1];
            lastCol.getFieldValueRect().setWidth(lastCol.getFieldValueRect().getWidth() / 2);
            sourceColEncoded = lastCol.encodeAsObject(1.0, true);
            sourceColEncoded.coords.x = sourceColEncoded.coords.x + sourceColEncoded.coords.w;
            targetFieldIdx = this.#childList[0].length;
        }
        sourceColEncoded.pageId = this.#pageId;
        let newCol = new Field(this.#layer, this.#pageId, this.#fieldId, 0, targetFieldIdx, Field.TYPE.COLUMN, 1.0, sourceColEncoded, this);
        newCol.#valueRect.setDraggable(true, false, false);
        if (this.#valueRect.isVisible()) {
            newCol.show();
        }
        if (this.#childList.length === 0) {
            this.#childList.push([]);
        }
        this.#childList[0].push(newCol);
    }

    valueRectBackup() {
        this.#valueRectBackup = this.#valueRect.encodeAsObject(1.0);
    }

    valueRectRestore() {
        if (this.#valueRectBackup) {
            this.#valueRect.set(this.#valueRectBackup);
            this.#valueRectBackup = undefined;
        }
    }

    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    getFieldAnchorRect() {
        return this.#label;
    }

    getFieldValueRect() {
        return this.#valueRect;
    }

    getFieldBottomAnchorRect() {
        return this.#bottomLabel;
    }

    getPageId() {
        return this.#pageId;
    }

    getAnchorUnderTablePageId() {
        return this.#anchorUnderTablePageId;
    }
    setAnchorUnderTablePageId(pageId) {
        this.#anchorUnderTablePageId = pageId;
        this.#showAnchorUnderTable = true;
        this.#bottomLabel.show();
        this.#bottomLabel.setDraggable(true);
        this.#bottomLabel.setResizable(true);
        this.#bottomLabel.select();
    }

    getFieldId() {
        return this.#fieldId;
    }

    setFieldId(newId) {
        this.#fieldId = newId;
        if (this.getChildList()) {
            this.getChildList().flat().forEach(child => child.setFieldId(newId));
        }
    }

    getRowId() {
        return this.#rowId;
    }

    getColId() {
        return this.#colId;
    }

    getChildList() {
        return this.#childList;
    }

    setType(type) {
        this.#type = type;
        if (this.#valueRect) {
            this.#valueRect.setFill(Field.#COLORS[this.#type]);
        }
        if ((this.isTypeTable() || this.isTypeTextExtendable()) && !this.#bottomLabel) {
            this.#createBottomLabelRect({x: this.#label.getX(), y: this.#valueRect.getY() + this.#valueRect.getHeight(), w: this.#label.getWidth(), h: this.#label.getHeight()});
            this.#attachEventDispatchersBottomLabel();
            this.#bottomLabel.setDraggable(true);
            this.#bottomLabel.setResizable(true);
            this.#bottomLabel.select();
            if (this.#hidden) {
                this.#bottomLabel.hide();
            }
        } else if (!(this.isTypeTable() || this.isTypeTextExtendable()) && this.#bottomLabel) {
            this.#bottomLabel.destroy();
            this.#bottomLabel = undefined;
        }
    }

    setDraggable(draggable) {
        if (this.#valueRect) {
            this.#valueRect.setDraggable(draggable);
        }
        if (this.#label) {
            this.#label.setDraggable(draggable);
        }
        if (this.#bottomLabel) {
            this.#bottomLabel.setDraggable(draggable);
        }
    }

    isTypeImage() {
        return this.#type === Field.TYPE.IMAGE;
    }

    isTypeTable() {
        return this.#type === Field.TYPE.TABLE;
    }

    isTypeOMR() {
        return this.#type === Field.TYPE.OMR;
    }

    isTypeColumn() {
        return this.#type === Field.TYPE.COLUMN;
    }

    isTypeTextExtendable() {
        return this.#type === Field.TYPE.TEXT_EXTENDABLE;
    }
}

/**
 * RecoForm Editor Class
 *
 * Initialization order:
 * 1) Instantiate RecoFormEditor with 'new' operator;
 * 2) Set readOnly to true, if readOnly mode required;
 * 3) Initialize RecoFormEditor, calling init() method;
 *
 * @type type
 */
class RecoFormEditor {
    static #ELEMENT_ID_PREVIEW_CONTAINER = 'f1:previewArea';
    static #ELEMENT_ID_PREVIEW_PAGINATOR = 'f1:pagePaginator';
    static #ELEMENT_ID_PREVIEW_IMAGE_CONTAINER = 'f1:previewImageArea';
    static #ELEMENT_ID_CONTAINER = 'pageViewContainer';
    static #ELEMENT_ID_FRD = 'f1:frdData';

    static #WV_ID_FORM_TYPE = 'formTypeWV';
    static #WV_ID_FORM_NAME_SI = 'rfSiWV';
    static #WV_ID_PREVIEW_CONTAINER = 'pagePaginatorWV';
    static #WV_ID_PREVIEW_MODE = 'PreviewFileModeWV';

    static #WV_ID_FIELDS_LIST = 'fieldsDTWV';
    static #WV_ID_PREFIX_BTN_FLD_REMOVE = 'btnFldRemoveWV';
    static #WV_ID_PREFIX_BTN_FLD_EDIT = 'btnFldEditWV';
    static #WV_ID_BTN_FLD_ADD = 'btnFldAddWV';

    //
    // Editor state
    //
    #initialized = false;
    #postInitCallbacks = [];

    //
    // Main config
    //
    #readOnly;
    #editMode;
    #recoDataFormType; // Valid for RecoWorkflow (view mode)
    #hideFields;
    #pageId;
    #pfPageIdx;
    #defaultRatio = {top: 0, right: 0, bottom: 0, left: 0};
    #defaultFieldValueRect = {x: 200, y: 0, w: 300, h: 150};
    #defaultFieldAnchorRect = {x: 0, y: 0, w: 200, h: 75};
    #defaultFieldType = Field.TYPE.TEXT;
    #defaultFieldRect;
    #initialFieldsScale = 1.0;
    #scaleBy = 0.25;

    //
    // External structure
    //
    #parentEL;
    #svgEL;
    #tooltipEL;

    //
    // Document structure
    //
    #groupHeaderFooter;
    #groupText;
    #groupFields;
    #fieldsList = [];
    #header;
    #footer;
    #bottomAnchorUseBottom;

    //
    // Editor state
    //
    #recoFieldEditor;
    #layoutMutationObserver;
    #fieldSettingsMutationObserver;
    #previewMutationObserver;
    #selectedField;
    #oldViewBox;

    constructor(readonly = false) {
        this.#readOnly = readonly;
        if (!this.#readOnly) {
            this.#scaleBy = 0.05;
        }

        this.refreshObservers();

        let childIds = [RecoFormEditor.#ELEMENT_ID_CONTAINER, RecoFormEditor.#ELEMENT_ID_PREVIEW_CONTAINER, RecoFormEditor.#ELEMENT_ID_PREVIEW_PAGINATOR, 'f1'];
        this.#previewMutationObserver = createMutationObserver(document, childIds, () => this.#previewMutationHandler());
    }

    /**
     * Append mutation observer to editor root element to properly bind to inputs state
     * Should be removed/replaced after switching to REST communication and editor UI state control via JS
     *
     * @returns {undefined}
     */
    refreshObservers() {
        if (document.getElementById('f1:rfCfg')) {
            this.#layoutMutationObserver = createMutationObserver('f1:rfCfg', ['f1:layoutSettingsArea'], () => this.#attachFieldListEvents());
            this.#fieldSettingsMutationObserver = createMutationObserver('f1:rfCfg', ['f1:fieldSettingsArea'], () => {
                if (this.#recoFieldEditor) {
                    this.#recoFieldEditor.init();
                }
            });
        }
    }

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

    /**
     * Builds initial editor view, including fields
     *
     * @returns {undefined}
     */
    init() {
        this.#initialized = false;
        this.#parentEL = document.getElementById(RecoFormEditor.#ELEMENT_ID_CONTAINER);
        if (!document.getElementById(RecoFormEditor.#ELEMENT_ID_CONTAINER)) {
            // Cannot find RecoFormEditor area;
            return;
        }
        if (!this.#parentEL.getAttribute('imgUrl') && !this.#parentEL.getAttribute('imgId')) {
            // Image ID not present on container - most probably it was already processed
            return;
        }
        this.#svgEL = createSvgElement("svg");
        this.#svgEL.setAttribute('visibility', 'hidden');
        this.#parentEL.appendChild(this.#svgEL);
        let bgImage = createSvgElement('image');
        this.#svgEL.appendChild(bgImage);

        if (this.#parentEL.getAttribute('imgUrl')) {
            bgImage.setAttribute('href', this.#parentEL.getAttribute('imgUrl'));
            this.#parentEL.removeAttribute('imgUrl');
        } else if (this.#parentEL.getAttribute('imgId')) {
            let imageSrc = document.getElementById(this.#parentEL.getAttribute('imgId'));
            bgImage.setAttribute('href', imageSrc.getAttribute('src'));
            this.#parentEL.removeAttribute('imgId');
            imageSrc.parentNode.removeChild(imageSrc);
        } else {
            console.error('Could not find page preview image');
        }
        bgImage.onload = () => {
            this.#svgEL.setAttribute('viewBox', '0 0 ' + this.#svgEL.getBBox().width + ' ' + this.#svgEL.getBBox().height);
            this.#initEditorContent();
            this.#svgEL.removeAttribute('visibility');
            while (this.#postInitCallbacks.length > 0) {
                let callback = this.#postInitCallbacks.shift();
                callback();
            }
            this.#initialized = true;
        };
    }

    #initEditorContent() {
        // Append containers for document structure
        this.#groupHeaderFooter = createSvgElement('g');
        this.#svgEL.appendChild(this.#groupHeaderFooter);

        this.#groupText = createSvgElement('g');
        this.#groupText.setAttribute('fill-opacity', 0.05);
        this.#svgEL.appendChild(this.#groupText);

        this.#groupFields = createSvgElement('g');
        this.#svgEL.appendChild(this.#groupFields);
        this.#groupFields.setAttribute('pointer-events', 'none');

        this.#processFrdData();
        this.#updateHeaderAndFooter();
        this.#drawTextLayer();
        if (getPrimeFacesWidgetIfAvailable('coordsWV')) {
            this.#drawFields();
        } else {
            this.#fieldsList.length = 0;
        }
        if (this.#hideFields) {
            this.#hideAll();
        }
        this.#attachGlobalFormEvents();
        this.#attachFieldListEvents();
        this.#attachWheelEvent();
        this.#attachSvgMoveEvent();

        // SVG for editor fully built - append it to DOM

        // Document loaded - hide spinner, if present
        if (this.#parentEL.getAttribute('spnrId')) {
            document.getElementById(this.#parentEL.getAttribute("spnrId")).style.display = 'none';
        }

        if (this.#recoFieldEditor) {
            this.#recoFieldEditor.setPageIdx(this.#pageId);
        }

        if (this.#selectedField) {
            let oldSelection = this.#selectedField;
            this.#selectedField = undefined;
            this.selectField(oldSelection.getPageId(), oldSelection.getFieldId(), oldSelection.getRowId(), oldSelection.getColId(), false);
            if (this.#recoFieldEditor) {
                this.#recoFieldEditor.init();
            }
        }
    }

    #setPage(pageIdx) {
        PF(RecoFormEditor.#WV_ID_PREVIEW_CONTAINER).paginator.setPage(pageIdx);
    }

    /**
     * Selects required field on editor, making it draggable/resizable and assigning proper event handlers depending on field's type
     * @param {Number} pageIdx
     * @param {Number} fieldIdx
     * @param {Number} rowIdx
     * @param {Number} colIdx
     * @param {Boolean} switchToRequiredPage Whether to switch to field's page, if it is located on another page
     * @returns {undefined}
     */
    selectField(pageIdx, fieldIdx, rowIdx = - 1, colIdx = - 1, switchToRequiredPage = true) {
        if (!this.#pageId !== undefined && pageIdx !== this.#pageId && switchToRequiredPage) {
            // Remember, that we need to select clicked field after page change
            this.clearSelection();
            this.#postInitCallbacks.push(() => this.selectField(pageIdx, fieldIdx, rowIdx, colIdx, false));
            // Switch to requested page
            this.#setPage(pageIdx);
            return;
        }
        if (!this.isEditMode() && !this.isReadOnly()) {
            this.clearSelection();
            this.#recoFieldEditor.destroy();
            this.#recoFieldEditor = undefined;
            return;
        }
        let newSelection = this.#findField(pageIdx, fieldIdx, rowIdx, colIdx);
        if (newSelection === this.#selectedField) {
            return;
        }

        this.clearSelection();

        if (!newSelection) {
            return;
        }

        this.#selectedField = newSelection;
        if (this.#selectedField.getFieldValueRect().getWidth() === 0 && this.#selectedField.getFieldValueRect().getHeight() === 0) {
            this.#selectedField.getFieldValueRect().decodeFromString(this.#defaultFieldRect, this.#initialFieldsScale);
            console.debug("Field's value region is not defined, falling back to default: " + this.#defaultFieldRect);
        }
        this.#selectedField.select();

        if (this.#readOnly && !newSelection.isTypeImage() && !newSelection.isTypeColumn()) {
            this.#hideAll();
            this.#selectedField.show();
            this.#scaleAndCenter();
        } else if (this.#readOnly && (newSelection.isTypeImage() || newSelection.isTypeColumn())) {
            this.#selectedField.valueRectBackup();
            this.#selectedField.setDraggable(true);
        }
        if (this.#recoFieldEditor) {
            this.#recoFieldEditor.setField(this.#selectedField);
        } else {
            this.#recoFieldEditor = new RecoFieldEditor(this, this.#selectedField, this.#initialFieldsScale);
            this.#recoFieldEditor.setDefaultExtenders(this.#defaultRatio);
        }
        this.#recoFieldEditor.setPageIdx(this.#pageId);
        if (!this.#readOnly) {
            // Disable pointer events on RecoForm editor, to make moving/resizing field rects easier, but keep them on document view (read-only mode)
            this.#groupText.setAttribute('pointer-events', 'none');
    }
    }

    #findField(pageIdx, fieldIdx, rowIdx = - 1, colIdx = - 1) {
        let field = this.#fieldsList
                .filter(field => field.getPageId() === pageIdx)
                .filter(field => field.getFieldId() === fieldIdx)[0];
        if (rowIdx !== -1 && colIdx !== -1) {
            field = field.getChildList()[rowIdx][colIdx];
        }
        return field;
    }

    selectFieldAndSetDefaultValueRect(pageIdx, fieldIdx, rowIdx = - 1, colIdx = - 1) {
        this.selectField(pageIdx, fieldIdx, rowIdx, colIdx);
        if (!this.#selectedField) {
            this.#selectedField = new Field(this.#groupFields, this.#pageId, this.#fieldsList.length, -1, -1, Field.TYPE.IMAGE, this.#initialFieldsScale, {coords: this.#defaultFieldValueRect, pageId: this.#pageId});
            this.#attachFieldEventListeners(this.#selectedField);
            if (this.#recoFieldEditor) {
                this.#recoFieldEditor.setField(this.#selectedField);
            } else {
                this.#recoFieldEditor = new RecoFieldEditor(this, this.#selectedField, this.#initialFieldsScale);
                this.#recoFieldEditor.setDefaultExtenders(this.#defaultRatio);
            }
            this.#selectedField.deleteOnDeselect = true;
        } else {
            this.#selectedField.deselect();
            this.#selectedField.getFieldValueRect().decodeFromString(this.#defaultFieldRect, this.#initialFieldsScale);
        }
        this.#selectedField.select();
    }

    #addField() {
        if (this.#selectedField) {
            this.clearSelection();
        }

        let newFieldData = {
            pageId: this.#pageId,
            coords: this.#defaultFieldValueRect,
            label: this.#defaultFieldAnchorRect
        };
        let fieldId = this.#fieldsList.filter(fld => fld.getPageId() === this.#pageId).length; // For new field, it's ID will be index of the field on the page, where it is added
        let newField = new Field(this.#groupFields, this.#pageId, fieldId, -1, -1, this.#defaultFieldType, this.#initialFieldsScale, newFieldData);
        let formTypeWidget = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_FORM_TYPE);
        if (formTypeWidget && this.#isHideFields(formTypeWidget.label[0].innerText)) {
            newField.hide();
        }
        this.#attachFieldEventListeners(newField);
        this.#fieldsList.push(newField);
        this.selectField(newField.getPageId(), newField.getFieldId());
    }

    /**
     * Deselects any previously selected fields and restores editor view settings to view's defaults
     *
     * @returns {undefined}
     */
    clearSelection() {
        if (this.#selectedField) {
            this.#selectedField.deselect();
            if (this.#readOnly) {
                this.#selectedField.valueRectRestore();
                this.#showAll();
                if (this.#oldViewBox) {
                    this.#svgEL.setAttribute('viewBox', this.#oldViewBox);
                }
            }
            if (this.#selectedField.deleteOnDeselect) {
                this.#selectedField.destroy();
            }
            this.#selectedField = undefined;
            this.#recoFieldEditor.destroy();
            this.#recoFieldEditor = undefined;
            this.#groupText.setAttribute('pointer-events', 'all');
        }
    }

    /**
     * Calculates new scale of the SVG and performs scaling, centering on cursor pointer
     *
     * @returns {undefined}
     */
    #scaleAndCenter() {
        // Remember old position and scale
        this.#oldViewBox = this.#svgEL.getAttribute('viewBox');

        // Calculate new scale. Use up to 80% of editor width
        let fieldValueRect = this.#selectedField.getFieldValueRect();


        let scale;
        if (fieldValueRect.getWidth() * 2 > this.#svgEL.getBBox().width * 0.8) {
            scale = (this.#svgEL.getBBox().width / 0.9) / fieldValueRect.getWidth();
        } else {
            scale = 2;
        }

        // Calculate newViewBox
        let cropW = fieldValueRect.getWidth() * scale;
        let cropH = (fieldValueRect.getHeight() + 200) * scale;
        let cropX = fieldValueRect.getX() - (cropW - fieldValueRect.getWidth()) / 2;
        let cropY = fieldValueRect.getY() - 200 / scale / 2;

        this.#svgEL.setAttribute('viewBox', `${cropX} ${cropY} ${cropW} ${cropH}`);
    }

    /**
     * Makes all fields/anchors on current page invisible
     *
     * @returns {undefined}
     */
    #hideAll() {
        this.#fieldsList.forEach((element) => element.hide());
    }

    /**
     * Makes all fields/anchors on current page visible
     *
     * @returns {undefined}
     */
    #showAll() {
        this.#fieldsList.forEach((element) => element.show());
    }

    #attachWheelEvent() {
        // zooming
        this.#svgEL.addEventListener('wheel', (e) => {
            if (!e.ctrlKey && this.#readOnly) {
                // Scroll  available only on Ctrl+mouse wheel
                if (!this.#tooltipEL) {
                    // Show tooltip only if it was not yet displayed
                    this.#tooltipEL = document.createElement("div");
                    this.#tooltipEL.innerHTML = this.#parentEL.dataset['scrollTooltip'];
                    this.#tooltipEL.className = "preview-scroll-tooltip";
                    this.#parentEL.append(this.#tooltipEL);
                    setTimeout(() => {
                        // Hide tooltip with effect
                        this.#tooltipEL.style.opacity = 0;
                        setTimeout(() => {
                            // Remove tooltip
                            this.#tooltipEL.parentNode.removeChild(this.#tooltipEL);
                            this.#tooltipEL = undefined;
                        }, 800);
                    }, 1500);
                }
                return;
            }

            e.preventDefault();

            // set the scaling factor (and make sure it's at least 10%)
            let scale = e.deltaY > 0 ? this.#scaleBy : 0 - this.#scaleBy;

            // get point in SVG space
            let pt = new DOMPoint(e.clientX, e.clientY);
            pt = pt.matrixTransform(this.#svgEL.getScreenCTM().inverse());

            // get viewbox transform
            let [x, y, width, height] = this.#svgEL.getAttribute('viewBox').split(' ').map(Number);

            // get pt.x as a proportion of width and pt.y as proportion of height
            let [xPropW, yPropH] = [(pt.x - x) / width, (pt.y - y) / height];

            // calc new width and height, new x2, y2 (using proportions and new width and height)
            let [width2, height2] = [width + width * scale, height + height * scale];
            let x2 = pt.x - xPropW * width2;
            let y2 = pt.y - yPropH * height2;

            this.#svgEL.setAttribute('viewBox', `${x2} ${y2} ${width2} ${height2}`);
        });
    }

    #attachSvgMoveEvent() {
        this.#svgEL.addEventListener('pointerdown', (e) => {
            this.#svgEL.drag = true;
            this.#svgEL.offset = {x: e.offsetX, y: e.offsetY};
            this.#svgEL.matrix = new DOMMatrix();
        });

        this.#svgEL.addEventListener('pointermove', (e) => {
            if (this.#svgEL.drag) {
                var tx = e.offsetX - this.#svgEL.offset.x;
                var ty = e.offsetY - this.#svgEL.offset.y;
                this.#svgEL.offset = {
                    x: e.offsetX,
                    y: e.offsetY
                };

                this.#svgEL.setAttribute('viewBox', `${this.#svgEL.viewBox.animVal.x - tx} ${this.#svgEL.viewBox.animVal.y - ty} ${this.#svgEL.viewBox.animVal.width} ${this.#svgEL.viewBox.animVal.height}`);
            }
        });

        this.#svgEL.addEventListener('pointerup', () => {
            this.#svgEL.drag = false;
        });
    }

    #attachGlobalFormEvents() {
        let formTypeWidget = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_FORM_TYPE);
        if (formTypeWidget) {
            this.#hideFieldsByFormType(formTypeWidget.label[0].innerText);
            formTypeWidget.getJQ().on('change', () => {
                let currentFormTypeWidget = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_FORM_TYPE);
                if (currentFormTypeWidget) {
                    this.#hideFieldsByFormType(currentFormTypeWidget.getActiveItem()[0].innerText);
                }
            });
        }
        let formNameWidget = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_FORM_NAME_SI);
        if (formNameWidget) {
            formNameWidget.getJQ().on('change', () => {
                this.#processFrdData();
                this.#fieldsList.forEach(field => field.destroy());
                this.#fieldsList.length = 0;
            });
        }
    }

    #hideFieldsByFormType(formType) {
        if (this.#isHideFields(formType)) {
            this.#hideAll();
        } else {
            this.#showAll();
        }
    }

    #isHideFields(formType) {
        return formType === 'Regex based' || formType === 'AI/LLM based';
    }

    /**
     * Attaches events to elements of field's list (edit, add and delete)
     *
     * @returns {undefined} */
    #attachFieldListEvents() {
        for (let fieldId = 0; ; fieldId++) {
            let btnFldRemoveEL = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_PREFIX_BTN_FLD_REMOVE + '_' + fieldId);
            let btnFldEditEL = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_PREFIX_BTN_FLD_EDIT + '_' + fieldId);
            if (!btnFldRemoveEL || !btnFldEditEL) {
                break;
            }
            if (!btnFldRemoveEL.getJQ().initialized) {
                btnFldRemoveEL.getJQ().on('click', () => this.#removeField(btnFldRemoveEL.getJQ().data().pageId, btnFldRemoveEL.getJQ().data().fieldId));
                btnFldRemoveEL.getJQ().initialized = true;
            }
            if (!btnFldEditEL.getJQ().initialized) {
                btnFldEditEL.getJQ().on('click', () => this.selectField(btnFldEditEL.getJQ().data().pageId, btnFldEditEL.getJQ().data().fieldId));
                btnFldEditEL.getJQ().initialized = true;
            }
        }

        let addFieldButton = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_BTN_FLD_ADD);
        if (addFieldButton && !addFieldButton.getJQ().initialized) {
            addFieldButton.getJQ().on('click', () => this.#addField());
            addFieldButton.getJQ().initialized = true;
        }
    }

    #removeField(pageIdx, fieldIdx) {
        let field = this.#findField(pageIdx, fieldIdx);
        if (!field) {
            return;
        }

        if (this.#selectedField && field.getPageId() === this.#selectedField.getPageId() && field.getFieldId() === this.#selectedField.getFieldId()) {
            this.clearSelection();
        }

        field.destroy();

        this.#fieldsList.splice(this.#fieldsList.indexOf(field), 1);

        this.#fieldsList
                .filter(fld => fld.getPageId() === field.getPageId())
                .filter(fld => fld.getFieldId() > field.getFieldId())
                .forEach(fld => fld.setFieldId(fld.getFieldId() - 1));
    }

    //
    // Inner methods
    //
    #updateHeaderAndFooter() {
        if (this.#readOnly) {
            // Do not display header and footer in read-only mode (document preview)
            return;
        }

        let headerEnabledControl = getPrimeFacesWidgetIfAvailable("headerEnabledWV");
        let headerHeightControl = getPrimeFacesWidgetIfAvailable("headerHeightWV");
        let headerKeywordsControl = getPrimeFacesWidgetIfAvailable("headerKwdsWV");
        let headerKeywordsAsBorderControl = getPrimeFacesWidgetIfAvailable('headerKeywordsAsBorderdWV');
        let headerAutoControl = getPrimeFacesWidgetIfAvailable('headerAutoWV');
        let footerEnabledControl = getPrimeFacesWidgetIfAvailable("footerEnabledWV");
        let footerHeightControl = getPrimeFacesWidgetIfAvailable("footerHeightWV");
        let footerKeywordsControl = getPrimeFacesWidgetIfAvailable("footerKwdsWV");
        let footerKeywordsAsBorderControl = getPrimeFacesWidgetIfAvailable('footerKeywordsAsBorderdWV');
        let footerAutoControl = getPrimeFacesWidgetIfAvailable('footerAutoWV');

        if (!headerEnabledControl || !headerHeightControl || !footerEnabledControl || !footerHeightControl) {
            // Current page is missing one of required controls for drawing header/footer
            return;
        }
        headerEnabledControl.getJQ().change(() => this.#updateHeaderAndFooter());
        headerHeightControl.getJQ().keyup(() => this.#updateHeaderAndFooter());
        footerEnabledControl.getJQ().change(() => this.#updateHeaderAndFooter());
        footerHeightControl.getJQ().keyup(() => this.#updateHeaderAndFooter());
        headerAutoControl.getJQ().change(() => {
            if (headerHeightControl.disabled) {
                headerHeightControl.enable();
                headerKeywordsControl.enable();
                headerKeywordsAsBorderControl.enable();
            } else {
                headerHeightControl.disable();
                headerKeywordsControl.disable();
                headerKeywordsAsBorderControl.disable();
            }
        });
        footerAutoControl.getJQ().change(() => {
            if (footerHeightControl.disabled) {
                footerHeightControl.enable();
                footerKeywordsControl.enable();
                footerKeywordsAsBorderControl.enable();
            } else {
                footerHeightControl.disable();
                footerKeywordsControl.disable();
                footerKeywordsAsBorderControl.disable();
            }
        });

        // Clean-up previously shown header and footer
        while (this.#groupHeaderFooter.firstChild) {
            this.#groupHeaderFooter.removeChild(this.#groupHeaderFooter.firstChild);
        }

        let headerConfig = {
            enabled: headerEnabledControl.isChecked(),
            auto: headerAutoControl.isChecked(),
            height: headerHeightControl.getValue()
        };

        let footerConfig = {
            enabled: footerEnabledControl.isChecked(),
            auto: footerAutoControl.isChecked(),
            height: footerHeightControl.getValue()
        };

        if (headerConfig.enabled) {
            if (this.isEditMode()) {
                headerHeightControl.enable();
                headerKeywordsControl.enable();
                headerKeywordsAsBorderControl.enable();
                headerAutoControl.enable();
            }
            this.#header = new Rectangle(this.#groupHeaderFooter, 0, 0, this.#svgEL.viewBox.animVal.width, this.#svgEL.viewBox.animVal.height * headerConfig.height / 100);
            this.#header.setFill('lightgray');
            this.#header.setOpacity(0.3);
            this.#header.setDraggable(false);
        } else {
            headerHeightControl.disable();
            headerKeywordsControl.disable();
            headerKeywordsAsBorderControl.disable();
            headerAutoControl.disable();
        }
        if (headerConfig.auto) {
            headerHeightControl.disable();
            headerKeywordsControl.disable();
            headerKeywordsAsBorderControl.disable();
        }
        if (footerConfig.enabled) {
            if (this.isEditMode()) {
                footerHeightControl.enable();
                footerKeywordsControl.enable();
                footerKeywordsAsBorderControl.enable();
                footerAutoControl.enable();
            }
            this.#footer = new Rectangle(this.#groupHeaderFooter, 0, this.#svgEL.viewBox.animVal.height * (100 - footerConfig.height) / 100, this.#svgEL.viewBox.animVal.width, this.#svgEL.viewBox.animVal.height * footerConfig.height / 100);
            this.#footer.setFill('lightgray');
            this.#footer.setOpacity(0.3);
            this.#footer.setDraggable(false);
        } else {
            footerHeightControl.disable();
            footerKeywordsControl.disable();
            footerKeywordsAsBorderControl.disable();
            footerAutoControl.disable();
        }
        if (footerConfig.auto) {
            footerHeightControl.disable();
            footerKeywordsControl.disable();
            footerKeywordsAsBorderControl.disable();
        }
    }

    #drawTextLayer() {
        if (!this.#parentEL.dataset.formId && !this.#parentEL.dataset.docId) {
            return;
        }
        let address;
        if (this.#readOnly) {
            let previewPreprocessed = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_PREVIEW_MODE)?.isChecked();
            if (!previewPreprocessed) {
                return;
            }
            address = CONTEXT_PATH + '/api/recoDoc/' + this.#parentEL.dataset.docId + '/' + ((this.#parentEL.dataset.dataId) ? this.#parentEL.dataset.dataId + '/' : '') + 'text/' + this.#pfPageIdx;
        } else {
            address = CONTEXT_PATH + '/api/recoForm/' + this.#parentEL.dataset.formId + '/text/' + this.#pageId;
        }

        var request = new XMLHttpRequest();
        request.open("GET", address, false);

        request.onload = () => {
            if (request.status !== 200) {
                return;
            }
            let responseJson = JSON.parse(request.response);
            if (responseJson.dims && responseJson.text) {
                if (responseJson.text.length !== 0) {
                    this.#generateTextSvgNode(responseJson.text);
                }
            }
        };
        request.onerror = (error) => console.error(error);
        request.send();
    }

    /**
     * Generates text-only preview SVG
     *
     * @param {type} dims
     */
    #generateTextSvgNode(text) {
        let textElement = undefined;
        let prevX = 0;
        let prevY = 0;

        // Process page text and add it to SVG
        text.forEach((symb) => {
            if (textElement === undefined) {
                // No text block was yet initialized
                textElement = createSvgElement("text");
                textElement.setAttribute('text-rendering', 'optimizeSpeed');
                textElement.addEventListener('pointerdown', (e) => e.stopPropagation());
                textElement.setAttribute("x", symb.region.x * this.#initialFieldsScale);
                textElement.setAttribute("y", symb.region.y * this.#initialFieldsScale);
            }
            if (symb.region.y > prevY && symb.region.x < prevX) {
                // To big distance to next char - most probably this is new line
                this.#groupText.appendChild(textElement);
                textElement = createSvgElement("text");
                textElement.setAttribute('text-rendering', 'optimizeSpeed');
                textElement.addEventListener('pointerdown', (e) => e.stopPropagation());
                textElement.setAttribute("x", symb.region.x * this.#initialFieldsScale);
                textElement.setAttribute("y", symb.region.y * this.#initialFieldsScale);
            }
            let text = createSvgElement("tspan");
            text.setAttribute("x", symb.region.x * this.#initialFieldsScale);
            text.setAttribute("y", symb.region.y * this.#initialFieldsScale + symb.region.h * this.#initialFieldsScale);
            text.setAttribute("width", symb.region.w * this.#initialFieldsScale);
            text.setAttribute("height", symb.region.h * this.#initialFieldsScale);
            text.setAttribute("font-size", symb.fontSize * this.#initialFieldsScale);
            text.textContent = symb.char;
            textElement.appendChild(text);
            prevX = symb.region.x;
            prevY = symb.region.y + symb.region.h / 2;
        });
        this.#groupText.appendChild(textElement);
    }

    /**
     * Creates Rect and Group objects on SVG area, representing all fields on currently displayed page
     *
     * @returns {undefined}
     */
    #drawFields() {
        let coordsJson = getPrimeFacesWidgetIfAvailable('coordsWV')?.getJQ().val();
        if (!coordsJson || coordsJson === "") {
            return;
        }
        this.#fieldsList.forEach(field => field.destroy());
        this.#fieldsList.length = 0;
        this.#fieldsList = JSON.parse(coordsJson)
                .filter(fieldData => fieldData.pageId === this.#pageId || fieldData.type === "TABLE")
                .map(fieldData => new Field(this.#groupFields, this.#pageId, fieldData.id, -1, -1, fieldData.type, this.#initialFieldsScale, fieldData));

        this.#fieldsList.forEach(field => this.#attachFieldEventListeners(field));

        if (this.#recoDataFormType === 2) {
            // TODO: As long as AI based form doesn't have per-cell bounding boxes - show main table area
            this.#fieldsList
                    .filter(field => field.isTypeTable())
                    .filter(field => field.getPageId() === this.#pageId)
                    .forEach(field => field.getFieldValueRect().show());
        }
    }

    #attachFieldEventListeners(field) {
        field.addEventListener('fieldClick', (e) => {
            if (this.isEditMode() && !this.#readOnly) {
                if (e.target && e.target !== this.#selectedField) {
                    // Call proper remoteCommand - it will also call 'selectField', if needed
                    this.#findFieldEditBtn(e.target.getPageId(), e.target.getFieldId()).getJQ().click();
                }
            }
        });
    }

    #findFieldEditBtn(pageId, fieldId) {
        for (let idx = 0; ; idx++) {
            let btnFldEditEL = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_PREFIX_BTN_FLD_EDIT + '_' + idx);
            if (!btnFldEditEL) {
                break;
            }
            if (btnFldEditEL.getJQ().data().pageId === pageId && btnFldEditEL.getJQ().data().fieldId === fieldId) {
                return btnFldEditEL;
            }
        }
    }

    /**
     * Reads FRD Data from respective container and applies located properties to this instance
     *
     * @returns {undefined}
     */
    #processFrdData() {
        let rfdDataHolder = document.getElementById(RecoFormEditor.#ELEMENT_ID_FRD);
        if (!rfdDataHolder || !rfdDataHolder.value || rfdDataHolder.value === "") {
            return;
        }

        let rfdData = JSON.parse(rfdDataHolder.value);
        if (rfdData.imageDpiScale) {
            this.#initialFieldsScale = rfdData.imageDpiScale;
        }
        if (rfdData.defaultExtRight) {
            this.#defaultRatio.right = rfdData.defaultExtRight;
        }
        if (rfdData.defaultExtLeft) {
            this.#defaultRatio.left = rfdData.defaultExtLeft;
        }
        if (rfdData.defaultExtTop) {
            this.#defaultRatio.top = rfdData.defaultExtTop;
        }
        if (rfdData.defaultExtBottom) {
            this.#defaultRatio.bottom = rfdData.defaultExtBottom;
        }
        if (rfdData.pageIdx || rfdData.pageIdx === 0) {
            this.#pageId = rfdData.pageIdx;
        }
        if (rfdData.pfPageIdx || rfdData.pfPageIdx === 0) {
            this.#pfPageIdx = rfdData.pfPageIdx;
        }
        if (rfdData.defaultFieldRect) {
            this.#defaultFieldRect = rfdData.defaultFieldRect;
        }
        if (rfdData.bottomAnchorUseBottom) {
            this.#bottomAnchorUseBottom = rfdData.bottomAnchorUseBottom;
        }
        if (rfdData.modeEdit !== undefined) {
            // TODO: Synchronize with WorkflowEditor.modeEdit();
            this.#editMode = rfdData.modeEdit;
        }
        if (rfdData.recoDataFormType !== undefined) {
            this.#recoDataFormType = rfdData.recoDataFormType;
        }
        if (rfdData.hideFields) {
            this.#hideFields = rfdData.hideFields;
        } else {
            this.#hideFields = false;
        }
        if (typeof rfdData.recoFormAltered !== 'undefined' && typeof rfdData.previewPreprocessed !== 'undefined') {
            this.#hideFields = this.#hideFields || rfdData.recoFormAltered || !rfdData.previewPreprocessed;
            this.#hideFields = this.#hideFields || isIE();
        }
    }

    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    isReadOnly() {
        return this.#readOnly;
    }

    isEditMode() {
        return this.#editMode;
    }

    /**
     * Returns list of SVG 'tspan' nodes with text, whose center lyes inside provided Rectangle
     *
     * @param {Rectangle} area
     * @returns {SVG.tspan[]}
     * */
    getTextInArea(area) {
        let text = Array.from(this.#groupText.childNodes)
                .map(group => Array.from(group.childNodes))
                .flat()
                .filter(char => area.containsCenter(new Rectangle(undefined, char.getAttribute('x'), char.getAttribute('y') - char.getAttribute('height'), char.getAttribute('width'), char.getAttribute('height'))));

        // 'strip' array - should be performed on characters array itself, so that retrieved text area will also match trimmed text area
        while (text.length !== 0 && /\s/.test(text[0].innerHTML)) {
            text.shift();
        }
        while (text.length !== 0 && /\s/.test(text[text.length - 1].innerHTML)) {
            text.pop();
        }

        return text;
    }

    isBottomAnchorUseBottom() {
        return this.#bottomAnchorUseBottom;
    }
}

/**
 * Editor, that is responsible for controlling RecoField properties/settings
 *
 * @type type */
class RecoFieldEditor {

    static #ELEMENT_ID_ROOT_NODE = 'f1:fieldSettingsArea';

    static #WV_ID_FIELD_COORDS = 'fieldCoordsWV';
    static #WV_ID_FIELD_TYPE = 'fieldTypeWV';
    static #WV_ID_BTN_FLD_SAVE = 'btnFldSaveWV';

    static #WV_ID_EXT_TOP = 'extTopWV';
    static #WV_ID_EXT_RIGHT = 'extRightWV';
    static #WV_ID_EXT_BOTTOM = 'extBottomWV';
    static #WV_ID_EXT_LEFT = 'extLeftWV';

    static #WV_ID_TABLE_BOTTOM_ANCHOR_DISABLED = 'tblAnchorUnderTableDisabledWV';
    static #WV_ID_TABLE_BOTTOM_ANCHOR_MOVE_CURR_PAGE = 'anchorUnderTableMoveToPageBtnWV';
    static #WV_ID_BOTTOM_ANCHOR_DISABLED = 'txtBottomAnchorDisabledWV';
    static #WV_ID_COL_LIST = 'tableColsWV';
    static #WV_ID_PREFIX_COL_IGNORED = 'colIgnoredWV';
    static #WV_ID_PREFIX_COL_REQUIRED = 'colRequiredWV';
    static #WV_ID_PREFIX_COL_DELETE = 'btnClnRemoveWV';
    static #WV_ID_COL_ADD = 'btnClmnAddWV';
    static #WV_ID_COL_EDIT = 'btnClmnEditWV';

    static #WV_ID_ANCHOR_TEXT = 'anchorTextWV';
    static #WV_ID_BOTTOM_ANCHOR_TEXT = 'bottomAnchorTextWV';

    static #WV_ID_OPMARKS_LIST = 'opMarksWV';
    static #WV_ID_PREFIX_OPMARK_DELETE = 'btnMarkRemoveWV';

    #recoFormEditor;
    #field;
    #scale;
    #pageIdx;
    #defaultExtenders;

    //
    // Field settings elements
    //
    #rootEL;

    constructor(recoFormEditor, field, scale) {
        this.#recoFormEditor = recoFormEditor;
        this.#rootEL = document.getElementById(RecoFieldEditor.#ELEMENT_ID_ROOT_NODE);
        this.#field = field;
        this.#scale = scale;
        this.#initPreviewTransformEvents();
    }

    init() {
        if (!this.#rootEL) {
            console.error('Could not find field settings area');
            return;
        }

        this.#initGeneralFieldSettings();
        this.#initExtendersFieldSettings();

        if (this.#field.isTypeTable()) {
            this.#initTableFieldSettings();
        }
        if (this.#field.isTypeOMR()) {
            this.#initOmrFieldSettings();
        }
        if (this.#field.isTypeTextExtendable()) {
            this.#initExtendableTextFieldSetting();
        }
    }

    destroy() {}

    //
    // UI Bindings --------------------------------------------------------------------------------
    //
    #initGeneralFieldSettings() {
        let fieldTypeWidget = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_FIELD_TYPE);
        if (fieldTypeWidget) {
            fieldTypeWidget.getJQ().on('change', () => {
                let currentFieldTypeWidget = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_FIELD_TYPE);
                if (currentFieldTypeWidget) {
                    this.#field.setType(currentFieldTypeWidget.getActiveItem()[0].innerText);
                }
            });
        }

        let saveFieldButton = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_BTN_FLD_SAVE);
        if (saveFieldButton) {
            saveFieldButton.getJQ().on('click', () => this.#recoFormEditor.clearSelection());
        }
    }

    #initPreviewTransformEvents() {
        this.#field.addEventListener('fieldTransformEnd', () => this.#refreshFieldCoords());
        this.#field.getChildList().flat().forEach(cell => cell.addEventListener('fieldTransformEnd', () => this.#refreshFieldCoords()));
        this.#field.addEventListener('fieldLabelTransformEnd', () => this.#refreshFieldData());
    }

    async #refreshFieldData() {
        if (this.#field.getFieldBottomAnchorRect() && this.#field.getFieldBottomAnchorRect().isVisible()) {
            let bottomAnchorContent = this.#getTextInArea(this.#field.getFieldBottomAnchorRect());
            let newAnchorVal = bottomAnchorContent.map(char => char.textContent).join('');
            let bottomAnchorRect;
            if (!PF(RecoFieldEditor.#WV_ID_BOTTOM_ANCHOR_TEXT).getJQ().val().startsWith('regex:')) {
                PF(RecoFieldEditor.#WV_ID_BOTTOM_ANCHOR_TEXT).getJQ().val(newAnchorVal);
                if (newAnchorVal !== '') {
                    PF(RecoFieldEditor.#WV_ID_BOTTOM_ANCHOR_TEXT).getJQ().change();
                    bottomAnchorRect = Rectangle.mergeAll(bottomAnchorContent.map(char => new Rectangle(undefined, char.getAttribute('x'), char.getAttribute('y') - char.getAttribute('height'), char.getAttribute('width'), char.getAttribute('height'))));
                    bottomAnchorRect.setY(bottomAnchorRect.getY());
                } else {
                    bottomAnchorRect = this.#field.getFieldBottomAnchorRect();
                }
            } else {
                bottomAnchorRect = this.#field.getFieldBottomAnchorRect();
            }
            this.#field.getFieldBottomAnchorRect().setX(bottomAnchorRect.getX());
            this.#field.getFieldBottomAnchorRect().setWidth(bottomAnchorRect.getWidth());
            if (this.#recoFormEditor.isBottomAnchorUseBottom()) {
                this.#field.getFieldBottomAnchorRect().setY(this.#field.getFieldBottomAnchorRect().getY());
                this.#field.getFieldBottomAnchorRect().setHeight(bottomAnchorRect.getY() + bottomAnchorRect.getHeight() - this.#field.getFieldBottomAnchorRect().getY());
            } else {
                this.#field.getFieldBottomAnchorRect().setY(bottomAnchorRect.getY());
                this.#field.getFieldBottomAnchorRect().setHeight(bottomAnchorRect.getHeight());
            }
        }
        if (this.#field.getFieldAnchorRect() && this.#field.getFieldAnchorRect().isVisible()) {
            let anchorContent = this.#getTextInArea(this.#field.getFieldAnchorRect());
            let newAnchorVal = anchorContent.map(char => char.textContent).join('');
            let anchorRect;
            if (!PF(RecoFieldEditor.#WV_ID_ANCHOR_TEXT).getJQ().val().startsWith('regex:')) {
                PF(RecoFieldEditor.#WV_ID_ANCHOR_TEXT).getJQ().val(newAnchorVal);
                if (newAnchorVal !== '') {
                    PF(RecoFieldEditor.#WV_ID_ANCHOR_TEXT).getJQ().change();
                    anchorRect = Rectangle.mergeAll(anchorContent.map(char => new Rectangle(undefined, char.getAttribute('x'), char.getAttribute('y') - char.getAttribute('height'), char.getAttribute('width'), char.getAttribute('height'))));
                    anchorRect.setY(anchorRect.getY());
                } else {
                    anchorRect = this.#field.getFieldAnchorRect();
                }
            } else {
                anchorRect = this.#field.getFieldAnchorRect();
            }
            this.#field.getFieldAnchorRect().setX(anchorRect.getX());
            this.#field.getFieldAnchorRect().setWidth(anchorRect.getWidth());
            this.#field.getFieldAnchorRect().setY(anchorRect.getY());
            this.#field.getFieldAnchorRect().setHeight(anchorRect.getHeight());
        }
        this.#refreshFieldCoords();
    }

    #initExtendersFieldSettings() {
        let extTopWidget = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_EXT_TOP);
        if (extTopWidget) {
            extTopWidget.getJQ().on('focus keyup', () => this.#drawSearchArea());
            extTopWidget.getJQ().on('blur', () => this.#hideSearchArea());
        }

        let extRightWidget = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_EXT_RIGHT);
        if (extRightWidget) {
            extRightWidget.getJQ().on('focus keyup', () => this.#drawSearchArea());
            extRightWidget.getJQ().on('blur', () => this.#hideSearchArea());
        }

        let extBottomWidget = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_EXT_BOTTOM);
        if (extBottomWidget) {
            extBottomWidget.getJQ().on('focus keyup', () => this.#drawSearchArea());
            extBottomWidget.getJQ().on('blur', () => this.#hideSearchArea());
        }

        let extLeftWidget = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_EXT_LEFT);
        if (extLeftWidget) {
            extLeftWidget.getJQ().on('focus keyup', () => this.#drawSearchArea());
            extLeftWidget.getJQ().on('blur', () => this.#hideSearchArea());
        }
    }

    #drawSearchArea() {
        // Extract extenders values from form
        let extTop = parseInt(PF(RecoFieldEditor.#WV_ID_EXT_TOP).getJQ().val());
        extTop = extTop === 0 ? this.#defaultExtenders.top : extTop;
        let extBottom = parseInt(PF(RecoFieldEditor.#WV_ID_EXT_BOTTOM).getJQ().val());
        extBottom = extBottom === 0 ? this.#defaultExtenders.bottom : extBottom;
        let extLeft = parseInt(PF(RecoFieldEditor.#WV_ID_EXT_LEFT).getJQ().val());
        extLeft = extLeft === 0 ? this.#defaultExtenders.left : extLeft;
        let extRight = parseInt(PF(RecoFieldEditor.#WV_ID_EXT_RIGHT).getJQ().val());
        extRight = extRight === 0 ? this.#defaultExtenders.right : extRight;

        this.#field.drawSearchArea(extTop, extRight, extBottom, extLeft);
    }

    #hideSearchArea() {
        this.#field.hideSearchArea();
    }

    #initTableFieldSettings() {
        let tableBottomAnchorDisabledWidget = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_TABLE_BOTTOM_ANCHOR_DISABLED);
        if (tableBottomAnchorDisabledWidget) {
            tableBottomAnchorDisabledWidget.getJQ().on('change', () => {
                let currentWidget = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_TABLE_BOTTOM_ANCHOR_DISABLED);
                if (currentWidget) {
                    this.#field.setBottomLabelDisabled(currentWidget.isChecked());
                }
            });
        }

        this.#initTableColsSettings();
        createMutationObserver(PF(RecoFieldEditor.#WV_ID_COL_LIST).getJQ().parent()[0], [PF(RecoFieldEditor.#WV_ID_COL_LIST).id], () => this.#initTableColsSettings());
        let moveTableAnchorButton = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_TABLE_BOTTOM_ANCHOR_MOVE_CURR_PAGE);
        if (moveTableAnchorButton) {
            moveTableAnchorButton.getJQ().on('click', () => this.#moveAnchorToPage());
        }
    }

    #moveAnchorToPage() {
        if (this.#pageIdx < this.#field.getPageId()) {
            // AnchorUnderTable can't be located before page itself
            return;
        }
        this.#field.setAnchorUnderTablePageId(this.#pageIdx);
        this.#field.getFieldBottomAnchorRect().setDraggable(true);
        this.#field.getFieldBottomAnchorRect().show();
    }

    #initOmrFieldSettings() {
        this.#initOpMarkSettings();
        createMutationObserver(PF(RecoFieldEditor.#WV_ID_OPMARKS_LIST).getJQ().parent()[0], [PF(RecoFieldEditor.#WV_ID_OPMARKS_LIST).id], () => this.#refreshOmrFieldData());
    }

    async #refreshOmrFieldData() {
        let fieldData = JSON.parse(PF(RecoFieldEditor.#WV_ID_FIELD_COORDS).getJQ().val());
        fieldData.pageId = this.#field.getPageId();
        if (fieldData.cols) {
            fieldData.cols.forEach(col => col.pageId = this.#field.getPageId());
        }
        if (fieldData.opMarks) {
            fieldData.opMarks.forEach(mark => mark.pageId = this.#field.getPageId());
        }
        this.#field.loadFieldData(fieldData, this.#pageIdx);
        this.#field.select();
        this.#initOpMarkSettings();
    }

    #initOpMarkSettings() {
        for (let rowIdx = 0; ; rowIdx++) {
            let btnMarkRemove = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_PREFIX_OPMARK_DELETE + '_' + rowIdx);
            if (!btnMarkRemove) {
                break;
            }
            let colDef = this.#field.getChildList()[0][rowIdx];
            btnMarkRemove.getJQ().on('click', () => {
                colDef.destroy();
                this.#field.getChildList()[0].splice(rowIdx, 1);
                PF(RecoFieldEditor.#WV_ID_OPMARKS_LIST).getTbody().children().remove(rowIdx);
                this.#refreshFieldCoords();
            });
        }
    }

    #initExtendableTextFieldSetting() {
        let bottomAnchorDisabledWidget = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_BOTTOM_ANCHOR_DISABLED);
        if (bottomAnchorDisabledWidget) {
            bottomAnchorDisabledWidget.getJQ().on('change', () => {
                let currentWidget = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_BOTTOM_ANCHOR_DISABLED);
                if (currentWidget) {
                    this.#field.setBottomLabelDisabled(currentWidget.isChecked());
                }
            });
        }
    }

    #initTableColsSettings() {
        for (let colIdx = 0; ; colIdx++) {
            let colIgnoredCheckbox = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_PREFIX_COL_IGNORED + '_' + colIdx);
            let colRequiredCheckbox = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_PREFIX_COL_REQUIRED + '_' + colIdx);
            let btnClnRemove = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_PREFIX_COL_DELETE + '_' + colIdx);
            let btnClnEdit = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_COL_EDIT + '_' + colIdx);
            if (!colIgnoredCheckbox || !colRequiredCheckbox || !btnClnRemove || !btnClnEdit) {
                break;
            }
            let colDef = this.#field.getChildList()[0][colIdx];
            colIgnoredCheckbox.getJQ().on('change', () => {
                if (colIgnoredCheckbox.isChecked()) {
                    colDef.show();
                    colRequiredCheckbox.enable();
                } else {
                    colDef.hide();
                    colRequiredCheckbox.disable();
                }
            });
            btnClnRemove.getJQ().on('click', () => {
                let tableRow = this.#field.getChildList()[0];
                if (colIdx !== 0) {
                    tableRow[colIdx - 1].getFieldValueRect().setWidth(tableRow[colIdx - 1].getFieldValueRect().getWidth() + tableRow[colIdx].getFieldValueRect().getWidth());
                } else if (colIdx === 0 && this.#field.getChildList()[0].length !== 1) {
                    tableRow[1].getFieldValueRect().setX(tableRow[0].getFieldValueRect().getX());
                    tableRow[1].getFieldValueRect().setWidth(tableRow[0].getFieldValueRect().getWidth() + tableRow[1].getFieldValueRect().getWidth());
                }
                colDef.destroy();
                tableRow.splice(colIdx, 1);
                PF(RecoFieldEditor.#WV_ID_COL_LIST).getTbody().children().remove(colIdx);
                this.#refreshFieldCoords();
            });
            btnClnEdit.getJQ().on('click', () => this.#recoFormEditor.selectField(btnClnEdit.getJQ().data().pageId, btnClnEdit.getJQ().data().fieldId, btnClnEdit.getJQ().data().rowId, btnClnEdit.getJQ().data().colId));
        }

        this.#field.getChildList().flat().forEach(cell => {
            cell.addEventListener('fieldClick', (e) => {
                // Call proper remoteCommand - it will also call 'selectField', if needed
                this.#findColEditBtn(e.target.getPageId(), e.target.getFieldId(), e.target.getRowId(), e.target.getColId()).getJQ().click();
            });
        });

        let addColumnButton = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_COL_ADD);
        if (addColumnButton && !addColumnButton.getJQ().initialized) {
            addColumnButton.getJQ().on('click', () => {
                this.#field.addColumn();
                this.#refreshFieldCoords();
            });
            addColumnButton.getJQ().initialized = true;
        }
    }

    #findColEditBtn(pageId, fieldId, rowId, colId) {
        for (let idx = 0; ; idx++) {
            let btnFldEditEL = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_COL_EDIT + '_' + idx);
            if (!btnFldEditEL) {
                break;
            }
            if (btnFldEditEL.getJQ().data().pageId === pageId && btnFldEditEL.getJQ().data().fieldId === fieldId && btnFldEditEL.getJQ().data().rowId === rowId && btnFldEditEL.getJQ().data().colId === colId) {
                return btnFldEditEL;
            }
        }
    }

    /**
     * Updates input field with actual coordinates of currently selected field
     *
     * @returns {undefined}
     */
    #refreshFieldCoords() {
        let value;
        if (this.#recoFormEditor.isReadOnly()) {
            value = this.#field.encodeAsString(1 / this.#scale);
        } else {
            value = JSON.stringify(this.#field.encodeAsObject(1 / this.#scale));
        }

        if (value) {
            PF(RecoFieldEditor.#WV_ID_FIELD_COORDS).getJQ().val(value);
            PF(RecoFieldEditor.#WV_ID_FIELD_COORDS).getJQ().change();
        }
    }

    /**
     *
     * @param {Rectangle} area
     * @returns {String} Text in requested area
     */
    #getTextInArea(area) {
        return this.#recoFormEditor.getTextInArea(area);
    }

    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    setPageIdx(pageIdx) {
        this.#pageIdx = pageIdx;
    }

    setField(field) {
        this.#field = field;
        this.#initPreviewTransformEvents();
    }

    setDefaultExtenders(extenders) {
        this.#defaultExtenders = extenders;
    }
}

var recoFormEditor;

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

function initRecoFormEditor(readOnly = false) {
    if (!recoFormEditor) {
        recoFormEditor = new RecoFormEditor(readOnly);
    } else {
        recoFormEditor.refreshObservers();
    }
    recoFormEditor.init();
}