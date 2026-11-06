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
package ai.eldoc.listener;

import com.mongodb.ReadPreference;
import com.mongodb.event.*;
import java.util.logging.Logger;
import static java.text.MessageFormat.format;


/**
 * MongoDB Cluster Listener
 * http://mongodb.github.io/mongo-java-driver/3.10/driver/reference/monitoring/
 */
public class MongoDbClusterListener implements ClusterListener {

    private static final Logger LOG = Logger.getLogger(MongoDbClusterListener.class.getName());

    private final ReadPreference readPreference;
    private boolean isWritable;
    private boolean isReadable;


    public MongoDbClusterListener(final ReadPreference readPreference) {
        this.readPreference = readPreference;
    }


    @Override
    public void clusterOpening(final ClusterOpeningEvent clusterOpeningEvent) {
        LOG.info(format("Cluster with unique client identifier {0} opening", clusterOpeningEvent.getClusterId()));
    }


    @Override
    public void clusterClosed(final ClusterClosedEvent clusterClosedEvent) {
        LOG.info(format("Cluster with unique client identifier {0} closed", clusterClosedEvent.getClusterId()));
    }


    @Override
    public void clusterDescriptionChanged(final ClusterDescriptionChangedEvent event) {
        if (!isWritable) {
            if (event.getNewDescription().hasWritableServer()) {
                isWritable = true;
                LOG.info("Writable server available!");
            }
        } else {
            if (!event.getNewDescription().hasWritableServer()) {
                isWritable = false;
                LOG.info("No writable server available!");
            }
        }

        if (!isReadable) {
            if (event.getNewDescription().hasReadableServer(readPreference)) {
                isReadable = true;
                LOG.info("Readable server available!");
            }
        } else {
            if (!event.getNewDescription().hasReadableServer(readPreference)) {
                isReadable = false;
                LOG.info("No readable server available!");
            }
        }
    }
}
