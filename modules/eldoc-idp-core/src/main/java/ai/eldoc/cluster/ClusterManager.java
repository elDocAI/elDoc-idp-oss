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
package ai.eldoc.cluster;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Cluster Manager
 */
@ApplicationScoped
public class ClusterManager {

    private static final Logger LOG = Logger.getLogger(ClusterManager.class.getName());

    @Resource(lookup = "payara/Hazelcast")
    private HazelcastInstance _instance;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public HazelcastInstance getInstance() {
        return _instance;
    }


    public IScheduledExecutorService getScheduledExecutorService() {
        return _instance.getScheduledExecutorService("default");
    }


    /**
     * Determines whether this node is primary or not based on the Hazelcast members list (1st most oldest node is the primary node),
     * so tasks like AD sync scheduling could be scheduled on this node
     *
     * @return
     */
    public boolean isPrimaryNode() {
        boolean isPrimaryNode = false;
        try {
            long start = System.currentTimeMillis();

            isPrimaryNode = _instance.getCluster().getMembers().iterator().next().localMember();

            long end = System.currentTimeMillis();

            LOG.log(Level.FINEST, "elDoc: isPrimaryNode={0} (determined in {1}ms)", new Object[]{isPrimaryNode, (end - start)});

            if ((end - start) > 1000) {
                // Log cases when it takes more than 1sec to determine is primary node or not
                LOG.log(Level.WARNING, "elDoc: isPrimaryNode determination delayed for {0}ms", (end - start));
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "elDoc: CRITICAL: can not determine isPrimaryNode (using default value isPrimaryNode=false)", e);
        }
        return isPrimaryNode;
    }


    /**
     * Returns UUID of the local member (current node which executes this request)
     *
     * @return
     */
    public String getLocalMemberId() {
        return _instance.getCluster().getLocalMember().getUuid().toString();
    }


    /**
     * Determines if the given memberId is the local memberId
     *
     * @param memberId
     * @return
     */
    public boolean isLocalMemberId(String memberId) {
        return Objects.equals(_instance.getCluster().getLocalMember().getUuid().toString(), memberId);
    }


    /**
     * Returns true in case cluster member with the given UUID still present (what also means active) in the cluster
     *
     * @param memberId
     * @return
     */
    public boolean isMemberPresent(String memberId) {
        return _instance.getCluster().getMembers().stream().anyMatch(member -> Objects.equals(member.getUuid().toString(), memberId));
    }
}
