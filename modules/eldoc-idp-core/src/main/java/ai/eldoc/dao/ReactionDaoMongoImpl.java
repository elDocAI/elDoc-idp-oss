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
import ai.eldoc.model.Reaction;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.ArrayList;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Reaction DAO MongoDB Implementation
 */
@ApplicationScoped
public class ReactionDaoMongoImpl extends AbstractMongoPojoDao<Reaction> implements ReactionDao {

    public static final String COLLECTION_NAME = "reactions";


    //
    // DAO Specific Business Methods --------------------------------------------------------------
    //
    private Bson getFilter(String author, String to, String refId, boolean hasMessage) {
        List<Bson> filters = new ArrayList<>();

        if (isNotBlank(author)) {
            filters.add(eq(Reaction._AUTHOR, author));
        }

        if (isNotBlank(to)) {
            filters.add(eq(Reaction._TO, to));
        }

        if (isNotBlank(refId)) {
            filters.add(eq(Reaction._REF_ITEM, refId));
        }

        if (hasMessage) {
            filters.add(gt(Reaction._MESSAGE, ""));
        }

        return filters.isEmpty()
                ? new Document()
                : and(filters);
    }


    @Override
    public List<Reaction> findBy(String author, String to, String refId) {
        List<Reaction> result = new ArrayList<>();
        getCollection().find(getFilter(author, to, refId, false)).collation(_collation).into(result);
        return result;
    }


    @Override
    public boolean deleteBy(String from, String to, String toDocID) {
        return getCollection().deleteMany(getFilter(from, to, toDocID, false)).wasAcknowledged();
    }


    @Override
    public long getCount(String from, String to, String toDocID, boolean hasMessage) {
        return getCollection().countDocuments(getFilter(from, to, toDocID, hasMessage), new CountOptions().collation(_collation));
    }


    //
    // DAO Specific Generic Methods ---------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public MongoCollection<Reaction> getCollection() {
        return super.getCollection(Reaction.class);
    }


    //
    // DAO Index Housekeeping methods -------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    protected List<IndexModel> getIndexesList() {
        List<IndexModel> indexes = new ArrayList<>();
        indexes.add(indexModelAsc(List.of(Reaction._AUTHOR, Reaction._REF_ITEM), indexOptionsText("idx_reactions_author_ref"))); // Covers filtering by author only and by author and ref together
        indexes.add(indexModelAsc(Reaction._TO, indexOptionsText("idx_reactions_to")));
        indexes.add(indexModelAsc(Reaction._TO, indexOptionsText("idx_reactions_to_with_message").partialFilterExpression(Filters.gt(Reaction._MESSAGE, ""))));
        indexes.add(indexModelAsc(Reaction._REF_ITEM, indexOptionsText("idx_reactions_ref")));
        return indexes;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void doIndexHousekeep() {
        doIndexHousekeep(getIndexesList());
    }

}
