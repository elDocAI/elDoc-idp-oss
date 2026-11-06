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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.context.ConfigContext;
import ai.eldoc.dao.mongo.AbstractMongoPojoDao;
import ai.eldoc.model.NumSequence;
import ai.eldoc.service.LogHelper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.text.SimpleDateFormat;
import java.util.*;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * NumberSequence DAO Mongo Implementation
 */
@ApplicationScoped
public class NumSequenceDaoMongoImpl extends AbstractMongoPojoDao<NumSequence> implements NumSequenceDao {

    public static final String COLLECTION_NAME = "numSeqs";

    @Inject
    private ConfigContext _configContext;


    @Override
    public MongoCollection<NumSequence> getCollection() {
        return super.getCollection(NumSequence.class);
    }


    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public NumSequence findByName(String counterName) {
        return find(eq(NumSequence._NAME, counterName));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<NumSequence> findAll() {
        return getCollection().find().into(new ArrayList<>());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getValue(String counterName, boolean updateCounter) {
        try {

            Bson filterDoc = eq(NumSequence._NAME, counterName);

            Calendar curDate = Calendar.getInstance();
            curDate.setTimeZone(TimeZone.getTimeZone(_configContext.getAppConfig().getSystemTimezone()));

            List<Bson> updateDoc = new ArrayList<>();

            if (updateCounter) {
                // Update VNUM field with new value, representing the one will be used during this getValue() call
                // Upon deciding, if VNUM field reset required, VDATE field is localized to System Timezone
                updateDoc.add(set(NumSequence._VNUM, new Document("$cond",
                        new Document("if",
                                new Document("$eq", Arrays.asList(
                                        curDate.get(Calendar.YEAR),
                                        new Document("$year", new Document("date", "$" + NumSequence._VDATE)
                                                .append("timezone", _configContext.getAppConfig().getSystemTimezone())))))
                                // If new Year value is equals to last saved in counter - increment counter
                                .append("then", new Document("$sum", Arrays.asList(
                                        "$" + NumSequence._VNUM,
                                        1
                                )))
                                // If new Year value differes from last saved in counter - reset counter to 1
                                .append("else", 1))));

                // Update VDATE field with new value, representing the one will be used during this getValue() call
                updateDoc.add(set(NumSequence._VDATE, curDate.getTime()));
            }

            // FindOneAndUpdate options to replicate old behaviour:
            // 1. On missing counter - do not create new record. Counter should be created during FormDefinition editing;
            // 2. NumSequence obtained from Database should be the one, that is modified by Update query - to replicate old behaviour
            // 3. In case updateCounter param is false - updateDoc is empty and no updates performed
            NumSequence counter = getCollection().findOneAndUpdate(filterDoc, updateDoc, new FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER));

            if (counter == null) {
                throw new ElDocRuntimeException("Requested NumSequence not found: " + counterName);
            }

            if (!updateCounter) {
                // Counter was not updated, so still holds old values. For valid preview of 'Next value' - we need to manually update it without saving to DB
                Calendar counterDate = Calendar.getInstance();
                counterDate.setTimeZone(TimeZone.getTimeZone(_configContext.getSystemTimezone()));
                counterDate.setTime(counter.getVDate());
                if (counterDate.get(Calendar.YEAR) == curDate.get(Calendar.YEAR)) {
                    counter.setVNum(counter.getVNum() + 1);
                } else {
                    counter.setVNum(1);
                }
                counter.setVDate(curDate.getTime());
            }

            return getValueFormatted(counter);

        } catch (Exception e) {
            LogHelper.setEvent("id00138", e, counterName);
        }
        return "-err-";
    }


    private String getValueFormatted(NumSequence counter) {

        Calendar cal = Calendar.getInstance();
        cal.setTime(counter.getVDate());

        String result;
        SimpleDateFormat df = new SimpleDateFormat();

        if (isBlank(counter.getVText())) {
            result = counter.getName() + "-" + counter.getVNum() + "/" + cal.get(Calendar.YEAR);
        } else {

            result = counter.getVText().replace("*N*", String.valueOf(counter.getVNum()));

            df.applyPattern("yyyy");
            result = result.replace("*YYYY*", df.format(cal.getTime()));

            df.applyPattern("yy");
            result = result.replace("*YY*", df.format(cal.getTime()));

            df.applyPattern("MM");
            result = result.replace("*MM*", df.format(cal.getTime()));

            df.applyPattern("dd");
            result = result.replace("*DD*", df.format(cal.getTime()));
        }
        return result;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected List<IndexModel> getIndexesList() {
        List<IndexModel> indexesList = new ArrayList<>();
        indexesList.add(indexModelAsc(NumSequence._NAME, indexOptionsTextUnique(null)));
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
