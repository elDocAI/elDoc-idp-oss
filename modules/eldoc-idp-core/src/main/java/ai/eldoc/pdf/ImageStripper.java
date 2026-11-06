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
package ai.eldoc.pdf;

import ai.eldoc.model.reco.Rectangle;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.*;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.util.Matrix;
import java.io.IOException;
import java.util.List;


/**
 * Image Processing Stripper Class
 *
 * https://svn.apache.org/repos/asf/pdfbox/tags/2.0.3/examples/src/main/java/org/apache/pdfbox/examples/util/PrintImageLocations.java
 */
public class ImageStripper extends PDFStreamEngine {

    private String _fullPageImageCandidate = null;
    private Rectangle _fullPageImageCandidateArea = null;
    private int _numberOfImages = 0;
    private boolean _verticalFlipRequired = false;
    private boolean _horizontalFlipRequired = false;
    private Rectangle _currentProcessedImage = null;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * Default constructor
     *
     * @throws IOException If there is an error loading text stripper properties.
     */
    public ImageStripper() throws IOException {
        addOperator(new Concatenate(this));
        addOperator(new DrawObject(this));
        addOperator(new SetGraphicsStateParameters(this));
        addOperator(new Save(this));
        addOperator(new Restore(this));
        addOperator(new SetMatrix(this));
    }


    /**
     * This is used to handle an operation.
     *
     * @param operator The operation to perform.
     * @param operands The list of arguments.
     *
     * @throws IOException If there is an error processing the operation.
     */
    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String operation = operator.getName();
        if ("Do".equals(operation)) {
            //processOperatorDebug(operator, operands);
            processOperatorForImage(operator, operands);
        }
        super.processOperator(operator, operands);
    }


    protected void processOperatorForImage(Operator operator, List<COSBase> operands) throws IOException {
        COSName objectName = (COSName) operands.get(0);
        if (getResources().isImageXObject(objectName)) {
            //PDImageXObject image = (PDImageXObject) getResources().getXObject(objectName);

            _numberOfImages++; // count the number of images on the page

            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();

            // Image position in user space units
            float imagePositionX = ctm.getTranslateX();
            float imagePositionY = ctm.getTranslateY();

            // Image displayed size in user space units
            float imageSizeW = ctm.getScalingFactorX();
            float imageSizeH = ctm.getScalingFactorY();

            // Check, if horizontal flip needed
            if (imageSizeW < 0) {
                imageSizeW = Math.abs(imageSizeW);
                imagePositionX = imagePositionX - imageSizeW;
                _horizontalFlipRequired = true;
            }

            // Check, if vertical flip needed
            if (imageSizeH < 0) {
                imageSizeH = Math.abs(imageSizeH);
                imagePositionY = imagePositionY - imageSizeH;
                _verticalFlipRequired = true;
            }

            // Page size
            float pageW = getCurrentPage().getCropBox().getWidth();
            float pageH = getCurrentPage().getCropBox().getHeight();

            _currentProcessedImage = new Rectangle(imagePositionX, pageH - imagePositionY - imageSizeH, imageSizeW, imageSizeH);

            if (_fullPageImageCandidate == null && isFullPageImageCandidate(_currentProcessedImage, new Rectangle(0, 0, pageW, pageH))) {
                // Assign full page image candidate in case it is found only if there was no full page image candidate previously found
                _fullPageImageCandidate = objectName.getName();
                _fullPageImageCandidateArea = new Rectangle(imagePositionX, imagePositionY, imageSizeW, imageSizeH);
            }
        }
    }


    /**
     * Checks, if provided image rect 'almost covers' page rect and, therefore, if corresponding image can be
     * full-page image candidate, based on the following rules:
     * <ul>
     * <li>Check if image location is inside 8% padding and image size more than 84% of the page size</li>
     * <li>Check if image location is inside 8% padding in width and it takes more than 40% of image area</li>
     * <li>Check if image location is inside 8% padding in height and it takes more than 40% of image area</li>
     * </ul>
     *
     * @param image
     * @param page
     * @return
     */
    protected boolean isFullPageImageCandidate(Rectangle image, Rectangle page) {
        Rectangle croppedImage = image.createIntersection(page);
        if (croppedImage.getX() <= (page.getWidth() * 0.08f) && croppedImage.getY() <= (page.getHeight() * 0.08f)
                && croppedImage.getWidth() >= (page.getWidth() * 0.84) && croppedImage.getHeight() >= (page.getHeight() * 0.84)) {
            return true;
        }
        if (croppedImage.getX() <= (page.getWidth() * 0.08f) && croppedImage.getWidth() >= (page.getWidth() * 0.84)
                && croppedImage.getArea() > page.getArea() * 0.4) {
            return true;
        }
        if (croppedImage.getY() <= (page.getHeight() * 0.08f) && croppedImage.getHeight() >= (page.getHeight() * 0.84)
                && croppedImage.getArea() > page.getArea() * 0.4) {
            return true;
        }
        return false;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns name of the Page Resource Image Object which is seems to be a full-page image
     *
     * @return
     */
    public String getFullPageImageCandidate() {
        return _fullPageImageCandidate;
    }



    /**
     * Returns area of the Page, where a full-page image seems to be located
     *
     * @return
     */
    public Rectangle getFullPageImageCandidateArea() {
        return _fullPageImageCandidateArea;
    }


    /**
     * Returns total number of located images
     *
     * @return
     */
    public int getNumberOfImages() {
        return _numberOfImages;
    }


    public boolean isVerticalFlipRequired() {
        return _verticalFlipRequired;
    }


    public boolean isHorizontalFlipRequired() {
        return _horizontalFlipRequired;
    }


    public Rectangle getCurrentProcessedImage() {
        return _currentProcessedImage;
    }


    public void setCurrentProcessedImage(Rectangle currentProcessedImage) {
        _currentProcessedImage = currentProcessedImage;
    }

}
