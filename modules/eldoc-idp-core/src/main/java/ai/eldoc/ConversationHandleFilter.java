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
package ai.eldoc;

import jakarta.enterprise.context.BusyConversationException;
import jakarta.enterprise.context.NonexistentConversationException;
import jakarta.servlet.*;
import java.io.IOException;


/**
 * Conversation Exceptions Handle Filter
 */
public class ConversationHandleFilter implements Filter {

//    In order to enable add the following lines to the web.xml:
//
//    <!-- Conversation Handle Filter for hendling exceptions related to the conversation restore (?cid= param) -->
//    <filter>
//        <filter-name>ConversationHandleFilter</filter-name>
//        <filter-class>ai.eldoc.ConversationHandleFilter</filter-class>
//    </filter>
//
//    <filter-mapping>
//        <filter-name>ConversationHandleFilter</filter-name>
//        <url-pattern>/*</url-pattern>
//    </filter-mapping>
//    <filter-mapping>
//        <filter-name>CDI Conversation Filter</filter-name>
//        <url-pattern>/*</url-pattern>
//    </filter-mapping>
//
//
//    <!-- Lazy conversation context initialization -->
//    <!-- https://docs.jboss.org/weld/reference/3.1.8.Final/en-US/html/scopescontexts.html#_lazy_and_eager_conversation_context_initialization -->
//    <context-param>
//        <param-name>org.jboss.weld.context.conversation.lazy</param-name>
//        <param-value>true</param-value>
//    </context-param>
    
    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (BusyConversationException e) {
            e.printStackTrace();
            response.setContentType("text/plain");
            response.getWriter().print("BusyConversationException");
        } catch (NonexistentConversationException e) {
            e.printStackTrace();
            response.setContentType("text/plain");
            response.getWriter().print("NonexistentConversationException");
        }
    }
}
