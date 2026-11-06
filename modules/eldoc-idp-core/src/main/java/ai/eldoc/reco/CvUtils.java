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
package ai.eldoc.reco;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.opencv.opencv_core.*;


/**
 * Computer Vision API Utils
 */
public class CvUtils {

    //
    // Resource cleanup methods -------------------------------------------------------------------
    //
    public static void close(Mat mat) {
        if (mat != null && !mat.isNull()) {
            mat.release();
            mat.deallocate();
            mat.close();
        }
    }


    public static void close(Indexer indexer) {
        if (indexer != null) {
            indexer.release();
            indexer.close();
        }
    }


    public static void close(KeyPoint keyPoint) {
        if (keyPoint != null && !keyPoint.isNull()) {
            keyPoint.deallocate();
            keyPoint.close();
        }
    }


    public static void close(KeyPointVector keyPointVector) {
        if (keyPointVector != null && !keyPointVector.isNull()) {
            for (KeyPoint keyPoint : keyPointVector.get()) {
                CvUtils.close(keyPoint);
            }
            keyPointVector.deallocate();
            keyPointVector.close();
        }
    }


    public static void close(MatVector matVector) {
        if (matVector != null && !matVector.isNull()) {
            for (Mat mat : matVector.get()) {
                CvUtils.close(mat);
            }
            matVector.deallocate();
            matVector.close();
        }
    }


    public static void close(Pointer pointer) {
        if (pointer != null && !pointer.isNull()) {
            pointer.deallocate();
            pointer.close();
        }
    }

}
