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
package ai.eldoc.dao;

import ai.eldoc.dao.mongo.AbstractMongoPojoDao;
import ai.eldoc.model.reco.RecoData;
import ai.eldoc.model.reco.RecoFormResult;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static com.mongodb.client.model.Filters.eq;
import static java.text.MessageFormat.format;


/**
 * Workflow Reco Extenstion Models DAO
 */
@ApplicationScoped
public class RecoDataDaoMongoImpl extends AbstractMongoPojoDao<RecoData> implements RecoDataDao {

    private static final Logger LOG = Logger.getLogger(RecoFormDaoMongoImpl.class.getName());

    public static final String COLLECTION_NAME = "recoData";

    @Inject
    private RecoDataFilesDao _recoDataFilesDao;


    //
    // DAO Specific Business Methods --------------------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    public List<RecoData> findAllByRefId(String refId) {
        List<RecoData> result = new ArrayList<>();
        getCollection().find(eq(RecoData._REF_ID, refId)).into(result);
        return result;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAllByRefId(ClientSession dbClientSession, String refId) {
        List<RecoData> listToDelete = findAllByRefId(refId);
        listToDelete.forEach(recoData -> {
            delete(dbClientSession, recoData);
        });
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public RecoData findByFileId(String fileId){
        return find(eq(RecoData._FILES + ".id", fileId));
    }


    /**
     * Loads RecoFormResult saved as separate file
     *
     * @param recoData - null-safe
     * @return
     */
    @Override
    public RecoFormResult getRecoFormResult(RecoData recoData) {
        if (recoData == null || recoData.getFileByTag(RecoData.FILE_TAG.IDP_RECORESULT.name()) == null) {
            return null;
        }
        try (InputStream is = _recoDataFilesDao.getFileDownloadStream(recoData.getFileByTag(RecoData.FILE_TAG.IDP_RECORESULT.name()))) {
            return RecoFormResult.fromJson(is);
        } catch (Exception ex) {
            String msg = format("File[{0}]: Error reading file", recoData.getFileByTag(RecoData.FILE_TAG.IDP_RECORESULT.name()).toString());
            LOG.log(Level.SEVERE, msg, ex);
        }
        return null;
    }


    //
    // DAO Specific Generic Methods ---------------------------------------------------------------
    //
    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    @Override
    public MongoCollection<RecoData> getCollection() {
        return super.getCollection(RecoData.class);
    }


    @Override
    public FilesDao getFilesDao() {
        return _recoDataFilesDao;
    }


    //
    // DAO Index Housekeeping methods -------------------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    protected List<IndexModel> getIndexesList() {
        List<IndexModel> indexesList = new ArrayList<>();

        IndexOptions indexOptions1 = new IndexOptions().background(true);
        indexesList.add(new IndexModel(new Document(RecoData._REF_ID, 1), indexOptions1));

        return indexesList;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void doIndexHousekeep() {
        doIndexHousekeep(getIndexesList());
    }
}
