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
package ai.eldoc.model;

import ai.eldoc.Util;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Interface declares Basic File Aware Model which also can store file attachments
 */
public interface BasicFileAwareModel extends BasicModel {

    /**
     * Standard field for storing file attachment references
     */
    public final static String _FILES = "_files";


    /**
     * Returns files list
     *
     * @return
     */
    @BsonProperty(_FILES)
    public List<DbFileRef> getFiles();


    /**
     * Returns all files found by the given tags
     *
     * @param tags
     * @return
     */
    default public List<DbFileRef> getFilesByTags(List<String> tags) {
        return getFiles().stream().filter(dbFile -> Util.hasIntersection(dbFile.getTags(), tags)).collect(Collectors.toList());
    }


    /**
     * Returns all files found by the given tag
     *
     * @param tag
     * @return
     */
    default public List<DbFileRef> getFilesByTag(String tag) {
        return getFiles().stream().filter(dbFile -> dbFile.getTags().contains(tag)).collect(Collectors.toList());
    }


    /**
     * Returns first file found by the given tag
     *
     * @param tag
     * @return
     */
    default public DbFileRef getFileByTag(String tag) {
        return getFiles().stream().filter(dbFile -> dbFile.getTags().contains(tag)).findFirst().orElse((DbFileRef) null);
    }


    /**
     * Removes reference to the file based on the provided tag (does not delete file from the DB!)
     *
     * @param tag
     */
    default public void removeFileByTag(String tag) {
        getFiles().removeIf(dbFile -> dbFile.getTags().contains(tag));
    }


    /**
     * Removes file from references list (does not delete file from the DB!)
     *
     * @param dbFile
     */
    default public void removeFile(DbFileRef dbFile) {
        getFiles().remove(dbFile);
    }

}
