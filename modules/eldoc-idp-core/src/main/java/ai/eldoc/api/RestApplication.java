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
package ai.eldoc.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.apache.shiro.web.jaxrs.ShiroFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import java.util.*;


/**
 * REST Application Configuration Class
 */
@ApplicationPath("/api")
public class RestApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        final Set<Class<?>> classes = new HashSet<>(super.getClasses());

        // Register resources and features
        classes.add(MultiPartFeature.class);

        // Register Shiro
        classes.add(ShiroFeature.class);

        return classes;
    }


    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jersey.config.server.provider.packages", "ai.eldoc.api");

        // Example below demonstrates how to switch default Jsonb-provider for JAX-RS to Jackson:
        // https://schoeffm.github.io/posts/switching-jaxrs-serializer-in-payara/
        // https://stackoverflow.com/questions/49793289/how-to-use-jackson-2-in-payara-5
        //properties.put("jersey.config.jsonFeature", "JacksonFeature");
        return properties;
    }
}
