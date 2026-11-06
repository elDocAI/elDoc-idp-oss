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
package ai.eldoc.dao.mongo;

import ai.eldoc.data.LazyQuery;
import ai.eldoc.exceptions.AclException;
import ai.eldoc.model.AclContext;
import ai.eldoc.model.BasicAclAwareModel;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Abstract Mongo DAO which stores common and base methods shared between other inherited DAOs
 */
public abstract class AbstractMongoDao {

    private static final Logger LOG = Logger.getLogger(AbstractMongoDao.class.getName());

    /**
     * Default case-insensitive collation with en locale
     */
    protected final static Collation _collation = Collation.builder().locale("en").collationStrength(CollationStrength.SECONDARY).build();


    //
    // Methods ------------------------------------------------------------------------------------
    //
    protected Bson aclCustomMatchCriteria(AclContext aclContext) {
        return null;
    }


    /**
     * Builds ACL filter criteria
     *
     * @param aclContext
     * @return Bson filter or null in case nothing was provided to build it
     */
    protected Bson aclMatchCriteria(AclContext aclContext) {
        if (aclContext == null) {
            throw new AclException("AclContext can not be null");
        }

        if (aclContext.isFullAccessMode()) {
            LOG.log(Level.FINEST, "FullAccess Mode enabled: {0}", aclContext);
            return null;
        }

        List<Bson> filtersList = new ArrayList<>();

        if (!aclContext.toSet().isEmpty()) {
            filtersList.add(in(BasicAclAwareModel._PATH_ACL_R, aclContext.toSet()));
            filtersList.add(in(BasicAclAwareModel._PATH_ACL_W, aclContext.toSet()));
        }

        if (!aclContext.getHier().isEmpty()) {
            filtersList.add(in(BasicAclAwareModel._PATH_ACL_RH, aclContext.getHier()));
            filtersList.add(in(BasicAclAwareModel._PATH_ACL_WH, aclContext.getHier()));
        }

        Bson customAclMatch = aclCustomMatchCriteria(aclContext);
        if (customAclMatch != null) {
            filtersList.add(customAclMatch);
        }

        if (!filtersList.isEmpty()) {
            return or(filtersList);
        } else {
            return null;
        }
    }


    protected Bson defaultMatchCriteria() {
        return null;
    }


    /**
     * Adds lookup request to embed data from the other collections
     *
     * @param aggregateList
     * @param lazyQuery
     * @param aclContext
     */
    protected void addLookupRequest(List<Bson> aggregateList, LazyQuery lazyQuery, AclContext aclContext) {
        // By default there is nothing to lookup/embed
    }


    /**
     * Applies Full-Text search to aggregation query<br>
     * IMPORTANT: Each DAO which is going to use findAllLazy() method have to override this method in order to make full-text search working properly
     *
     * @param searchFulltext
     * @return
     */
    protected Bson searchFtMatchCriteria(String searchFulltext) {
        // Samlpe implementation (be considerate on using regex with MongoDB):
        //Pattern regex = Pattern.compile(Pattern.quote(searchFulltext), Pattern.CASE_INSENSITIVE);
        //return or(eq(Model._FIELD1, regex), eq(Model._FIELD2, regex));
        LOG.log(Level.WARNING, "Full-Text Search request ignored. Method searchFtMatchCriteria() not implemented for this DAO: {0}", this.getClass().getName());
        return new Document();
    }


    protected List<Bson> buildAggregatesList(LazyQuery lazyQuery, AclContext aclContext) {
        List<Bson> aggregateList = new ArrayList<>();
        // Apply ACL match if any
        Bson aclMatchFilter = aclMatchCriteria(aclContext);
        if (aclMatchFilter != null) {
            aggregateList.add(Aggregates.match(aclMatchFilter));
        }

        // Apply default matching due to DAO specifics to avoid possibility of overriding base DAO filter by runtime filter
        if (defaultMatchCriteria() != null) {
            aggregateList.add(Aggregates.match(defaultMatchCriteria()));
        }

        // add lookup request
        addLookupRequest(aggregateList, lazyQuery, aclContext);

        // add filters passed via lazyQuery
        aggregateList.add(new Document("$match", lazyQuery.getFilter()));

        // add searchFT filter
        if (isNotBlank(lazyQuery.getSearchFT())) {
            aggregateList.add(Aggregates.match(searchFtMatchCriteria(lazyQuery.getSearchFT().trim())));
        }
        return aggregateList;
    }

}
