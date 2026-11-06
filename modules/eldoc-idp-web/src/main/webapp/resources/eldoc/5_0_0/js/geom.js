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

class Rectangle extends EventTarget {
    static #OPACITY_DEFAULT = 0.3;
    static #LABEL_OPACITY = 0.6;

    static #DEFAULT_CONFIG = {
        stroke: 'black',
        strokeWidth: 1,
        hitStrokeWidth: 0,
        strokeScaleEnabled: false,
        shadowForStrokeEnabled: false,
        ignoreStroke: true,
        opacity: Rectangle.#OPACITY_DEFAULT,
        perfectDrawEnabled: false
    };

    static OUT_LEFT = 1;
    static OUT_TOP = 2;
    static OUT_RIGHT = 4;
    static OUT_BOTTOM = 8;

    #layer;
    #rectUI;
    #transformer;
    #boundBoxFunc;

    // configuration
    #draggable = false;
    #resizable = false;
    #preserveRatio;
    #allowVerticalDrag;
    #allowHorizontalDrag;
    #activeCursorStyle = 'move';

    // Constraints for resizing
    #widthMin = -1;
    #widthMax = -1;
    #heightMin = -1;
    #heightMax = -1;

    // drag-related fields
    #offset = {x: 0, y: 0};

    constructor(layer, x, y, w, h, active = false) {
        super();
        this.#layer = layer;

        this.#rectUI = createSvgElement('rect');
        this.#rectUI.setAttribute('shape-rendering', 'optimizeSpeed');
        this.#rectUI.rect = this;
        Object.assign(this.#rectUI.style, Rectangle.#DEFAULT_CONFIG);
        this.setX(x);
        this.setY(y);
        this.setWidth(w);
        this.setHeight(h);
        if (this.#layer) {
            this.#layer.appendChild(this.#rectUI);
            this.#attachDragEvents();
        }
        if (active) {
            this.#rectUI.setAttribute('pointer-events', 'all');
            this.#rectUI.setAttribute('cursor', this.#activeCursorStyle);
        } else {
            this.#rectUI.setAttribute('pointer-events', 'none');
            this.#rectUI.removeAttribute('cursor');
    }
    }

    #attachDragEvents() {
        this.#rectUI.onpointerdown = (e) => this.#eventMouseDown(e);
    }

    #eventMouseDown(e) {
        e.stopPropagation();
        if (e.button !== undefined && e.button !== 0) {
            // Process only primary button clicks and touches
            return;
        }
        const event = new CustomEvent('rectClick');
        this.dispatchEvent(event);

        if (!this.#draggable) {
            return;
        }
        e.currentTarget.isMouseDown = true;

        e.currentTarget.offset = {x: e.offsetX, y: e.offsetY};
        e.currentTarget.addEventListener('pointermove', this.#eventMouseMove);
        e.currentTarget.addEventListener('pointerup', this.#eventMouseUp);
        if (e.pointerId !== undefined) {
            e.currentTarget.setPointerCapture(e.pointerId);
        }
    }

    #eventMouseMove(e) {
        e.stopPropagation();
        if (!e.currentTarget.isMouseDown) {
            return;
        }
        if (!e.currentTarget.isDragging) {
            // This is first 'mouse move' event after click - this marks action as 'dragging'
            // This allows as to differentiate simple 'click' and 'drag' events
            e.currentTarget.isDragging = true;
        }

        if (Date.now() - e.currentTarget.lastMove < 30) {
            return;
        }

        let dx = (e.currentTarget.rect.isAllowHorizontalDrag() ? e.offsetX - e.currentTarget.offset.x : 0) / e.srcElement.getScreenCTM().a;
        let dy = (e.currentTarget.rect.isAllowVerticalDrag() ? e.offsetY - e.currentTarget.offset.y : 0) / e.srcElement.getScreenCTM().a;
        e.currentTarget.offset = {
            x: e.offsetX,
            y: e.offsetY
        };
        if (Math.abs(dx) >= 1) {
            e.currentTarget.rect.setX(e.currentTarget.rect.getX() + dx);
            e.currentTarget.offset.x = e.offsetX;
        }
        if (Math.abs(dy) >= 1) {
            e.currentTarget.rect.setY(e.currentTarget.rect.getY() + dy);
            e.currentTarget.offset.y = e.offsetY;
        }

        if (Math.abs(dx) >= 1 || Math.abs(dy) >= 1) {
            if (e.currentTarget.rect.getTransformer()) {
                e.currentTarget.rect.getTransformer().updateAnchorsPosition();
            }

            const event = new CustomEvent('rectDragMove', {detail: {dx: dx, dy: dy}});
            e.currentTarget.rect.dispatchEvent(event);
            e.currentTarget.lastMove = Date.now();
        }
    }

    #eventMouseUp(e) {
        e.stopPropagation();
        if (!e.currentTarget.isMouseDown) {
            return;
        }
        if (e.currentTarget.isDragging) {
            const event = new CustomEvent('rectTransformEnd');
            e.currentTarget.rect.dispatchEvent(event);
        }
        delete e.currentTarget.isMouseDown;
        delete e.currentTarget.isDragging;
        delete e.currentTarget.offset;
        if (e.pointerId !== undefined) {
            e.currentTarget.releasePointerCapture(e.pointerId);
        }
        e.currentTarget.removeEventListener('pointermove', e.currentTarget.rect.#eventMouseMove);
        e.currentTarget.removeEventListener('pointerup', e.currentTarget.rect.#eventMouseUp);
    }

    scale(scale) {
        this.setX(this.getX() * scale);
        this.setY(this.getY() * scale);
        this.setWidth(this.getWidth() * scale);
        this.setHeight(this.getHeight() * scale);
    }

    encodeAsObject(scale = 1.0) {
        return {
            x: this.#round(this.getX() * scale),
            y: this.#round(this.getY() * scale),
            w: this.#round(this.getWidth() * scale),
            h: this.#round(this.getHeight() * scale)
        };
    }

    encodeAsString(scale = 1.0) {
        return "("
                + this.#round(this.getX() * scale) + ";"
                + this.#round(this.getY() * scale) + ";"
                + this.#round(this.getWidth() * scale) + ";"
                + this.#round(this.getHeight() * scale)
                + ")";
    }

    #round(value) {
        return Math.round(value * 100_000) / 100_000;
    }

    decodeFromString(newArea, scale = 1.0) {
        if (!newArea) {
            return;
        }
        let matches = newArea.match(/\((\d+(\.\d+)?);(\d+(\.\d+)?);(\d+(\.\d+)?);(\d+(\.\d+)?)\)/);
        if (matches.length !== 9) {
            return;
        }
        this.setX(parseInt(matches[1]) * scale);
        this.setY(parseInt(matches[3]) * scale);
        this.setWidth(parseInt(matches[5]) * scale);
        this.setHeight(parseInt(matches[7]) * scale);
    }

    /**
     * Checks, if this rectangle intersects with other rectangle
     *
     * @param {Rectangle} other
     * @returns {Boolean}
     */
    intersects(other) {
        return this.getX() < other.getX() + other.getWidth()
                && this.getX() + this.getWidth() > other.getX()
                && this.getY() < other.getY() + other.getHeight()
                && this.getY() + this.getHeight() > other.getY();
    }

    static merge(first, second) {
        let x = Math.min(first.getX(), second.getX());
        let y = Math.min(first.getY(), second.getY());
        let w = Math.max(first.getX() + first.getWidth(), second.getX() + second.getWidth()) - x;
        let h = Math.max(first.getY() + first.getHeight(), second.getY() + second.getHeight()) - y;
        return new Rectangle(undefined, x, y, w, h);
    }

    static mergeAll(rects) {
        if (rects.length === 0) {
            return undefined;
        }
        let result = rects[0];
        for (let i = 1; i < rects.length; i++) {
            result = Rectangle.merge(result, rects[i]);
        }
        return result;
    }

    /**
     * Checks, if this rectangle contains center of the other rectangle
     *
     * @param {Rectangle} other
     * @returns {Boolean}
     */
    containsCenter(other) {
        return this.getX() < other.getX() + other.getWidth() / 2
                && this.getX() + this.getWidth() > other.getX() + other.getWidth() / 2
                && this.getY() < other.getY() + other.getHeight() / 2
                && this.getY() + this.getHeight() > other.getY() + other.getHeight() / 2;
    }

    moveToBottom() {
        if (this.#rectUI === this.#layer.firstChild) {
            return;
        }
        this.#layer.removeChild(this.#rectUI);
        this.#layer.prepend(this.#rectUI);
    }

    moveToTop() {
        if (this.#rectUI === this.#layer.lastChild) {
            return;
        }
        this.#layer.removeChild(this.#rectUI);
        this.#layer.appendChild(this.#rectUI);
    }

    #removeTransformerAnchor(transformers, anchor) {
        let anchorIndex = transformers.indexOf(anchor);
        return transformers.splice(anchorIndex, 1);
    }

    select(transformerAnchors) {
        this.deselect();
        if (!transformerAnchors) {
            transformerAnchors = [Transformer.ANCHORS.TOP, Transformer.ANCHORS.TOP_RIGHT, Transformer.ANCHORS.RIGHT, Transformer.ANCHORS.BOTTOM_RIGHT, Transformer.ANCHORS.BOTTOM, Transformer.ANCHORS.BOTTOM_LEFT, Transformer.ANCHORS.LEFT, Transformer.ANCHORS.TOP_LEFT];
        }
        if (this.isPreserveRatio()) {
            this.#removeTransformerAnchor(transformerAnchors, Transformer.ANCHORS.TOP);
            this.#removeTransformerAnchor(transformerAnchors, Transformer.ANCHORS.RIGHT);
            this.#removeTransformerAnchor(transformerAnchors, Transformer.ANCHORS.BOTTOM);
            this.#removeTransformerAnchor(transformerAnchors, Transformer.ANCHORS.LEFT);
        }
        this.moveToTop();
        let rootNode = this.#layer;
        while (rootNode.nodeName !== 'svg') {
            rootNode = rootNode.parentNode;
        }
        if (this.#resizable) {
            if (this.#transformer) {
                this.#transformer.show();
            } else {
                this.#transformer = new Transformer(this.#layer, transformerAnchors, rootNode.getScreenCTM().a);
                this.#transformer.attach(this);
                if (this.#boundBoxFunc) {
                    this.#transformer.setBoundBoxFunc(this.#boundBoxFunc);
                }
                this.#transformer.addEventListener('transformend', () => {
                    const event = new CustomEvent('rectTransformEnd');
                    this.dispatchEvent(event);
                });
            }
            this.#transformer.addEventListener('rectDragMove', () => {
                const event = new CustomEvent('rectDragMove');
                this.dispatchEvent(event);
            });
            if (!this.#draggable || !this.isVisible()) {
                this.#transformer.hide();
            }
        }
    }

    deselect() {
        if (this.#transformer) {
            this.#transformer.destroy();
            this.#transformer = undefined;
        }
    }

    show() {
        this.#rectUI.setAttribute('visibility', 'visible');
        this.#rectUI.setAttribute('pointer-events', 'all');
        if (this.#transformer && this.#draggable) {
            this.#transformer.show();
        }
    }

    hide() {
        this.#rectUI.setAttribute('visibility', 'hidden');
        this.#rectUI.setAttribute('pointer-events', 'none');
        if (this.#transformer) {
            this.#transformer.hide();
        }
    }

    destroy() {
        this.#rectUI.parentNode.removeChild(this.#rectUI);
        this.#rectUI = undefined;
        if (this.#transformer) {
            this.#transformer.destroy();
            this.#transformer = undefined;
        }
    }

    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    getX() {
        return parseFloat(this.#rectUI.getAttribute('x'));
    }

    setX(x) {
        this.#rectUI.setAttribute('x', x);
        if (this.#transformer) {
            this.#transformer.updateAnchorsPosition();
        }
    }

    getY() {
        return parseFloat(this.#rectUI.getAttribute('y'));
    }

    setY(y) {
        this.#rectUI.setAttribute('y', y);
        if (this.#transformer) {
            this.#transformer.updateAnchorsPosition();
        }
    }

    getWidth() {
        return parseFloat(this.#rectUI.getAttribute('width'));
    }

    setWidth(w) {
        this.#rectUI.setAttribute('width', w);
        if (this.#transformer) {
            this.#transformer.updateAnchorsPosition();
        }
    }

    getWidthMin() {
        return this.#widthMin;
    }

    setWidthMin(width) {
        this.#widthMin = width;
    }

    getWidthMax() {
        return this.#widthMax;
    }

    setWidthMax(width) {
        this.#widthMax = width;
    }

    getHeight() {
        return parseFloat(this.#rectUI.getAttribute('height'));
    }

    setHeight(h) {
        this.#rectUI.setAttribute('height', h);
        if (this.#transformer) {
            this.#transformer.updateAnchorsPosition();
        }
    }

    getHeightMin() {
        return this.#heightMin;
    }

    setHeightMin(height) {
        this.#heightMin = height;
    }

    getHeightMax() {
        return this.#heightMax;
    }

    setHeightMax(height) {
        this.#heightMax = height;
    }

    getBoundingBox() {
        return {
            x: this.getX(),
            y: this.getY(),
            w: this.getWidth(),
            h: this.getHeight()
        };
    }

    getTransformer() {
        return this.#transformer;
    }

    isAllowHorizontalDrag() {
        return this.#allowHorizontalDrag;
    }

    isAllowVerticalDrag() {
        return this.#allowVerticalDrag;
    }

    set(boundingBox) {
        this.setX(boundingBox.x);
        this.setY(boundingBox.y);
        this.setWidth(boundingBox.w);
        this.setHeight(boundingBox.h);
    }

    setFill(color) {
        this.#rectUI.style.fill = color;
    }

    setOpacity(opacity) {
        this.#rectUI.style.opacity = opacity;
    }

    setActiveCursorStyle(cursorStyle) {
        this.#activeCursorStyle = cursorStyle;
        if (this.#draggable) {
            this.#rectUI.setAttribute('cursor', this.#activeCursorStyle);
        }
    }

    setDraggable(draggable, allowHorizontalDrag = true, allowVerticalDrag = true) {
        this.#draggable = draggable;
        if (draggable) {
            this.#rectUI.setAttribute('pointer-events', 'all');
            this.#rectUI.setAttribute('cursor', this.#activeCursorStyle);
            this.#allowVerticalDrag = allowVerticalDrag;
            this.#allowHorizontalDrag = allowHorizontalDrag;
            if (this.#transformer) {
                this.#transformer.show();
            }
        } else {
            this.#rectUI.setAttribute('pointer-events', 'none');
            this.#rectUI.removeAttribute('cursor');
            if (this.#transformer) {
                this.#transformer.hide();
            }
    }
    }

    isResizable() {
        return this.#resizable;
    }

    setResizable(resizable) {
        this.#resizable = resizable;
    }

    isPreserveRatio() {
        return this.#preserveRatio;
    }

    setPreserveRatio(preserveRatio) {
        this.#preserveRatio = preserveRatio;
    }

    setBoundBoxFunc(func) {
        this.#boundBoxFunc = func;
    }

    isVisible() {
        return this.#rectUI.getAttribute('visibility') !== 'hidden';
    }
}

class Transformer extends EventTarget {
    static ANCHORS = {
        'TOP': 'TOP',
        'TOP_RIGHT': 'TOP_RIGHT',
        'RIGHT': 'RIGHT',
        'BOTTOM_RIGHT': 'BOTTOM_RIGHT',
        'BOTTOM': 'BOTTOM',
        'BOTTOM_LEFT': 'BOTTOM_LEFT',
        'LEFT': 'LEFT',
        'TOP_LEFT': 'TOP_LEFT'
    }

    static #ANCHOR_OPACITY = 1.0;
    static #ANCHOR_COLOR = 'white';

    #rect;

    #boundBoxFunc;

    #anchors = [];
    #anchorSize = 8;

    constructor(layer, anchors, anchorScale = 1.0) {
        super();
        this.#anchorSize = this.#anchorSize / anchorScale;
        this.#anchors = anchors.map(anchorType => this.#createAnchor(anchorType, layer));
    }

    attach(rect) {
        this.#rect = rect;
        this.#anchors.forEach(anchor => {
            switch (anchor.type) {
                case Transformer.ANCHORS.TOP:
                    this.#configureAnchorT(anchor);
                    this.#updateAnchorTPosition(anchor);
                    break;
                case Transformer.ANCHORS.TOP_RIGHT:
                    this.#configureAnchorTR(anchor);
                    this.#updateAnchorTRPosition(anchor);
                    break;
                case Transformer.ANCHORS.RIGHT:
                    this.#configureAnchorR(anchor);
                    this.#updateAnchorRPosition(anchor);
                    break;
                case Transformer.ANCHORS.BOTTOM_RIGHT:
                    this.#configureAnchorBR(anchor);
                    this.#updateAnchorBRPosition(anchor);
                    break;
                case Transformer.ANCHORS.BOTTOM:
                    this.#configureAnchorB(anchor);
                    this.#updateAnchorBPosition(anchor);
                    break;
                case Transformer.ANCHORS.BOTTOM_LEFT:
                    this.#configureAnchorBL(anchor);
                    this.#updateAnchorBLPosition(anchor);
                    break;
                case Transformer.ANCHORS.LEFT:
                    this.#configureAnchorL(anchor);
                    this.#updateAnchorLPosition(anchor);
                    break;
                case Transformer.ANCHORS.TOP_LEFT:
                    this.#configureAnchorTL(anchor);
                    this.#updateAnchorTLPosition(anchor);
                    break;
            }
        });

        this.#anchors.forEach(anchor => this.#attachTransformEvent(anchor));
        this.#anchors.forEach(anchor => this.#attachTransformEndEvent(anchor));

        // Make anchors visible
        this.show();
    }

    updateAnchorsPosition() {
        this.#anchors.forEach(anchor => {
            switch (anchor.type) {
                case Transformer.ANCHORS.TOP:
                    this.#updateAnchorTPosition(anchor);
                    break;
                case Transformer.ANCHORS.TOP_RIGHT:
                    this.#updateAnchorTRPosition(anchor);
                    break;
                case Transformer.ANCHORS.RIGHT:
                    this.#updateAnchorRPosition(anchor);
                    break;
                case Transformer.ANCHORS.BOTTOM_RIGHT:
                    this.#updateAnchorBRPosition(anchor);
                    break;
                case Transformer.ANCHORS.BOTTOM:
                    this.#updateAnchorBPosition(anchor);
                    break;
                case Transformer.ANCHORS.BOTTOM_LEFT:
                    this.#updateAnchorBLPosition(anchor);
                    break;
                case Transformer.ANCHORS.LEFT:
                    this.#updateAnchorLPosition(anchor);
                    break;
                case Transformer.ANCHORS.TOP_LEFT:
                    this.#updateAnchorTLPosition(anchor);
                    break;
            }
        });
    }

    show() {
        this.#anchors.forEach(anchor => anchor.show());
    }

    hide() {
        this.#anchors.forEach(anchor => anchor.hide());
    }

    destroy() {
        this.#anchors.forEach(anchor => anchor.destroy());
    }

    #createAnchor(type, layer) {
        let anchor = new Rectangle(layer, 0, 0, this.#anchorSize, this.#anchorSize, true);
        anchor.setFill(Transformer.#ANCHOR_COLOR);
        anchor.setOpacity(Transformer.#ANCHOR_OPACITY);
        anchor.type = type;
        anchor.hide();
        return anchor;
    }

    #attachTransformEvent(anchor) {
        anchor.addEventListener('rectDragMove', (e) => {
            const event = new CustomEvent('rectDragMove');
            this.dispatchEvent(event);
        });
    }

    #attachTransformEndEvent(anchor) {
        anchor.addEventListener('rectTransformEnd', (e) => {
            const event = new CustomEvent('transformend');
            this.dispatchEvent(event);
        });
    }

    #configureAnchorT(anchor) {
        anchor.position = Rectangle.OUT_TOP;
        anchor.setDraggable(true, false, true);
        anchor.setActiveCursorStyle('ns-resize');
        anchor.addEventListener('rectDragMove', (e) => {
            let newBBox = {x: this.#rect.getX(), y: this.#rect.getY() + e.detail.dy, w: this.#rect.getWidth(), h: this.#rect.getHeight() - e.detail.dy};
            this.#checkNewRectAndUpdate(newBBox, anchor);
        });
    }

    #updateAnchorTPosition(anchor) {
        anchor.setX(this.#rect.getX() + this.#rect.getWidth() / 2 - this.#anchorSize / 2);
        anchor.setY(this.#rect.getY() - this.#anchorSize / 2);
    }

    #configureAnchorTR(anchor) {
        anchor.position = Rectangle.OUT_TOP | Rectangle.OUT_RIGHT;
        anchor.setDraggable(true, true, true);
        anchor.setActiveCursorStyle('nesw-resize');
        anchor.addEventListener('rectDragMove', (e) => {
            let newBBox = {x: this.#rect.getX(), y: this.#rect.getY() + e.detail.dy, w: this.#rect.getWidth() + e.detail.dx, h: this.#rect.getHeight() - e.detail.dy};
            this.#checkNewRectAndUpdate(newBBox, anchor);
        });
    }

    #updateAnchorTRPosition(anchor) {
        anchor.setX(this.#rect.getX() + this.#rect.getWidth() - this.#anchorSize / 2);
        anchor.setY(this.#rect.getY() - this.#anchorSize / 2);
    }

    #configureAnchorR(anchor) {
        anchor.position = Rectangle.OUT_RIGHT;
        anchor.setDraggable(true, true, false);
        anchor.setActiveCursorStyle('ew-resize');
        anchor.addEventListener('rectDragMove', (e) => {
            let newBBox = {x: this.#rect.getX(), y: this.#rect.getY(), w: this.#rect.getWidth() + e.detail.dx, h: this.#rect.getHeight()};
            this.#checkNewRectAndUpdate(newBBox, anchor);
        });
    }

    #updateAnchorRPosition(anchor) {
        anchor.setX(this.#rect.getX() + this.#rect.getWidth() - this.#anchorSize / 2);
        anchor.setY(this.#rect.getY() + this.#rect.getHeight() / 2 - this.#anchorSize / 2);
    }

    #configureAnchorBR(anchor) {
        anchor.position = Rectangle.OUT_BOTTOM | Rectangle.OUT_RIGHT;
        anchor.setDraggable(true, true, true);
        anchor.setActiveCursorStyle('nwse-resize');
        anchor.addEventListener('rectDragMove', (e) => {
            let newBBox = {x: this.#rect.getX(), y: this.#rect.getY(), w: this.#rect.getWidth() + e.detail.dx, h: this.#rect.getHeight() + e.detail.dy};
            this.#checkNewRectAndUpdate(newBBox, anchor);
        });
    }

    #updateAnchorBRPosition(anchor) {
        anchor.setX(this.#rect.getX() + this.#rect.getWidth() - this.#anchorSize / 2);
        anchor.setY(this.#rect.getY() + this.#rect.getHeight() - this.#anchorSize / 2);
    }

    #configureAnchorB(anchor) {
        anchor.position = Rectangle.OUT_BOTTOM;
        anchor.setDraggable(true, false, true);
        anchor.setActiveCursorStyle('ns-resize');
        anchor.addEventListener('rectDragMove', (e) => {
            let newBBox = {x: this.#rect.getX(), y: this.#rect.getY(), w: this.#rect.getWidth(), h: this.#rect.getHeight() + e.detail.dy};
            this.#checkNewRectAndUpdate(newBBox, anchor);
        });
    }

    #updateAnchorBPosition(anchor) {
        anchor.setX(this.#rect.getX() + this.#rect.getWidth() / 2 - this.#anchorSize / 2);
        anchor.setY(this.#rect.getY() + this.#rect.getHeight() - this.#anchorSize / 2);
    }

    #configureAnchorBL(anchor) {
        anchor.position = Rectangle.OUT_BOTTOM | Rectangle.OUT_LEFT;
        anchor.setDraggable(true, true, true);
        anchor.setActiveCursorStyle('nesw-resize');
        anchor.addEventListener('rectDragMove', (e) => {
            let newBBox = {x: this.#rect.getX() + e.detail.dx, y: this.#rect.getY(), w: this.#rect.getWidth() - e.detail.dx, h: this.#rect.getHeight() + e.detail.dy};
            this.#checkNewRectAndUpdate(newBBox, anchor);
        });
    }

    #updateAnchorBLPosition(anchor) {
        anchor.setX(this.#rect.getX() - this.#anchorSize / 2);
        anchor.setY(this.#rect.getY() + this.#rect.getHeight() - this.#anchorSize / 2);
    }

    #configureAnchorL(anchor) {
        anchor.position = Rectangle.OUT_LEFT;
        anchor.setDraggable(true, true, false);
        anchor.setActiveCursorStyle('ew-resize');
        anchor.addEventListener('rectDragMove', (e) => {
            let newBBox = {x: this.#rect.getX() + e.detail.dx, y: this.#rect.getY(), w: this.#rect.getWidth() - e.detail.dx, h: this.#rect.getHeight()};
            this.#checkNewRectAndUpdate(newBBox, anchor);
        });
    }

    #updateAnchorLPosition(anchor) {
        anchor.setX(this.#rect.getX() - this.#anchorSize / 2);
        anchor.setY(this.#rect.getY() + this.#rect.getHeight() / 2 - this.#anchorSize / 2);
    }

    #configureAnchorTL(anchor) {
        anchor.position = Rectangle.OUT_TOP | Rectangle.OUT_LEFT;
        anchor.setDraggable(true, true, true);
        anchor.setActiveCursorStyle('nwse-resize');
        anchor.addEventListener('rectDragMove', (e) => {
            let newBBox = {x: this.#rect.getX() + e.detail.dx, y: this.#rect.getY() + e.detail.dy, w: this.#rect.getWidth() - e.detail.dx, h: this.#rect.getHeight() - e.detail.dy};
            this.#checkNewRectAndUpdate(newBBox, anchor);
        });
    }

    #updateAnchorTLPosition(anchor) {
        anchor.setX(this.#rect.getX() - this.#anchorSize / 2);
        anchor.setY(this.#rect.getY() - this.#anchorSize / 2);
    }

    #getScalePreservingRatio(newBBox) {
        return Math.abs(Math.min(newBBox.w / this.#rect.getWidth(), newBBox.h / this.#rect.getHeight()));
    }

    #checkNewRectAndUpdate(newBBox, anchor) {
        if (this.#rect.getWidthMax() !== -1 && newBBox.w > this.#rect.getWidthMax()) {
            if ((anchor.position & Rectangle.OUT_LEFT) === Rectangle.OUT_LEFT) {
                newBBox.x = newBBox.x + (newBBox.w - this.#rect.getWidthMax());
            }
            newBBox.w = this.#rect.getWidthMax();
        }
        if (this.#rect.getWidthMin() !== -1 && newBBox.w < this.#rect.getWidthMin()) {
            if ((anchor.position & Rectangle.OUT_LEFT) === Rectangle.OUT_LEFT) {
                newBBox.x = newBBox.x + (newBBox.w - this.#rect.getWidthMin());
            }
            newBBox.w = this.#rect.getWidthMin();
        }
        if (this.#rect.getHeightMax() !== -1 && newBBox.h > this.#rect.getHeightMax()) {
            if ((anchor.position & Rectangle.OUT_TOP) === Rectangle.OUT_TOP) {
                newBBox.y = newBBox.y + (newBBox.h - this.#rect.getHeightMax());
            }
            newBBox.h = this.#rect.getHeightMax();
        }
        if (this.#rect.getHeightMin() !== -1 && newBBox.h < this.#rect.getHeightMin()) {
            if ((anchor.position & Rectangle.OUT_TOP) === Rectangle.OUT_TOP) {
                newBBox.y = newBBox.y + (newBBox.h - this.#rect.getHeightMin());
            }
            newBBox.h = this.#rect.getHeightMin();
        }
        if (this.#rect.isPreserveRatio()) {
            let scale = this.#getScalePreservingRatio(newBBox);
            let newWidth = this.#rect.getWidth() * scale;
            let newHeight = this.#rect.getHeight() * scale;
            if ((anchor.position & Rectangle.OUT_LEFT) === Rectangle.OUT_LEFT) {
                newBBox.x = newBBox.x + (newBBox.w - newWidth);
            }
            newBBox.w = this.#rect.getWidth() * scale;
            if ((anchor.position & Rectangle.OUT_TOP) === Rectangle.OUT_TOP) {
                newBBox.y = newBBox.y + (newBBox.h - newHeight);
            }
            newBBox.h = this.#rect.getHeight() * scale;
        }

        if (this.#boundBoxFunc) {
            newBBox = this.#boundBoxFunc(this.#rect.getBoundingBox(), newBBox, this.#rect);
            if (newBBox.x === this.#rect.getX() && newBBox.y === this.#rect.getY() && newBBox.w === this.#rect.getWidth() && newBBox.h === this.#rect.getHeight()) {
                // New bounding box (rect shape) was declined by bounding box function - do not update target rect
                this.updateAnchorsPosition();
                return;
            }
        }
        if (newBBox.w <= 10 || newBBox.h <= 10) {
            this.updateAnchorsPosition();
            return;
        }
        this.#rect.set(newBBox);
        this.updateAnchorsPosition();
    }

    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    setBoundBoxFunc(func) {
        this.#boundBoxFunc = func;
    }
}