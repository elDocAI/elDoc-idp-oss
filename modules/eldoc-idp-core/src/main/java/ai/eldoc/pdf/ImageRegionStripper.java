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
import ai.eldoc.util.RectangleUtils;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;


/**
 *
 */
public class ImageRegionStripper extends ImageStripper {

    private static final Logger LOG = LoggerFactory.getLogger(ImageRegionStripper.class);

    /**
     * Determines the ration of the intersection's area to original image area to decide, if the image
     * can be chosen as a candidate for requested image. <br/>
     * 0.85 allows for around 7.5% gap on both sides
     */
    private static final double REQUIRED_IMAGE_AREA_INTERSECTION = 0.85;
    private static final double REQUIRED_REGION_AREA_INTERSECTION = 0.65;

    private final Map<Rectangle, PDImageXObject> _regionImages;
    private final Map<Rectangle, List<PDImageXObject>> _regionImageCandidates;
    private final Map<Rectangle, List<Rectangle>> _regionImageCandidatesArea;
    private final Map<Rectangle, List<Boolean>> _regionImageCandidateHorizontalFlipRequired;
    private final Map<Rectangle, List<Boolean>> _regionImageCandidateVerticalFlipRequired;
    private final Map<Rectangle, Boolean> _regionHorizontalFlipRequired;
    private final Map<Rectangle, Boolean> _regionVerticalFlipRequired;
    private final List<Rectangle> _regionArea;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ImageRegionStripper() throws IOException {
        super();
        _regionImages = new HashMap<>();
        _regionImageCandidates = new HashMap<>();
        _regionImageCandidatesArea = new HashMap<>();
        _regionArea = new ArrayList<>();
        _regionHorizontalFlipRequired = new HashMap<>();
        _regionVerticalFlipRequired = new HashMap<>();
        _regionImageCandidateHorizontalFlipRequired = new HashMap<>();
        _regionImageCandidateVerticalFlipRequired = new HashMap<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void addRegion(Rectangle rect) {
        _regionArea.add(rect);
    }


    @Override
    protected void processOperatorForImage(Operator operator, List<COSBase> operands) throws IOException {
        super.processOperatorForImage(operator, operands);

        COSName objectName = (COSName) operands.get(0);
        if (getResources().isImageXObject(objectName)) {
            checkAndProcessRegionExactMatch(objectName);
            checkAndProcessRegionApproxMatch(objectName);
        }
    }


    /**
     * Checks all regions, defined in the stripper, and if region matches exactly with provided imageRect - it is
     * saved as region value
     *
     * @param objectName
     */
    protected void checkAndProcessRegionExactMatch(COSName objectName) {
        _regionArea.forEach(region -> {
            if (region.getX() == getCurrentProcessedImage().getX()
                    && region.getY() == getCurrentProcessedImage().getY()
                    && region.getWidth() == getCurrentProcessedImage().getWidth()
                    && region.getHeight() == getCurrentProcessedImage().getHeight()) {
                try {
                    _regionImages.put(region, (PDImageXObject) getResources().getXObject(objectName));
                    _regionHorizontalFlipRequired.put(region, isHorizontalFlipRequired());
                    _regionVerticalFlipRequired.put(region, isVerticalFlipRequired());
                } catch (IOException ex) {
                    LOG.error(null, ex);
                }
            }
        });
    }


    /**
     * Checks all regions, defined in the stripper, and if region intersection with the image is bigger, than defined
     * threshold (in percents from original image area) - then it is saved as region value candidate
     *
     * @param objectName
     */
    protected void checkAndProcessRegionApproxMatch(COSName objectName) {
        _regionArea.forEach(region -> {
            if (!getCurrentProcessedImage().intersects(region)) {
                return;
            }

            if (RectangleUtils.getIntersectionRatio(getCurrentProcessedImage(), region) > REQUIRED_REGION_AREA_INTERSECTION) {
                List<PDImageXObject> regionCandidatesList = _regionImageCandidates.computeIfAbsent(region, k -> new ArrayList<>());
                List<Rectangle> regionCandidatesAreaList = _regionImageCandidatesArea.computeIfAbsent(region, k -> new ArrayList<>());
                try {
                    regionCandidatesList.add((PDImageXObject) getResources().getXObject(objectName));
                    regionCandidatesAreaList.add(getCurrentProcessedImage());
                } catch (IOException ex) {
                    LOG.error(null, ex);
                }
                List<Boolean> horizontalFlip = _regionImageCandidateHorizontalFlipRequired.computeIfAbsent(region, k -> new ArrayList<>());
                List<Boolean> verticalFlip = _regionImageCandidateVerticalFlipRequired.computeIfAbsent(region, k -> new ArrayList<>());
                horizontalFlip.add(isHorizontalFlipRequired());
                verticalFlip.add(isVerticalFlipRequired());
            }
        });
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public PDImageXObject getRegionImage(Rectangle rect) {
        return _regionImages.get(rect);
    }


    public List<PDImageXObject> getRegionImageCandidates(Rectangle region) {
        return _regionImageCandidates.getOrDefault(region, new ArrayList<>());
    }


    public List<Rectangle> getRegionImageCandidateAreas(Rectangle region) {
        return _regionImageCandidatesArea.getOrDefault(region, new ArrayList<>());
    }


    public boolean getRegionHorizontalFlipRequired(Rectangle region) {
        return _regionHorizontalFlipRequired.get(region);
    }


    public boolean getRegionVerticalFlipRequired(Rectangle region) {
        return _regionVerticalFlipRequired.get(region);
    }


    public boolean isRegionImageCandidateHorizontalFlipRequired(Rectangle rect, int candidateIdx) {
        return _regionImageCandidateHorizontalFlipRequired.get(rect).get(candidateIdx);
    }


    public boolean isRegionImageCandidateVerticalFlipRequired(Rectangle rect, int candidateIdx) {
        return _regionImageCandidateVerticalFlipRequired.get(rect).get(candidateIdx);
    }
}
