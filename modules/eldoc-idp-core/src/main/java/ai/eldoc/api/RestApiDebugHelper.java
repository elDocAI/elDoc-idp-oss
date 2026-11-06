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

import jakarta.json.JsonObject;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * API Debug helper class
 */
public class RestApiDebugHelper {

    private static final Logger LOG = Logger.getLogger(RestApiDebugHelper.class.getName());


    //
    // Constructor --------------------------------------------------------------------------------
    //
    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static void diagRequest(HttpServletRequest request) {
        debugRequestMainParams(request);
        debugRequestHeaders(request);
        debugRequestCookies(request);
        debugRequestBody(request);
    }


    private static void debugRequestMainParams(HttpServletRequest request) {
        LOG.log(Level.FINEST, "-------------------------------> MAIN");
        // request url string: http://beta.eldoc.dev4.local/elDoc/beta/elDoc.nsf/xRes.xsp/test/bbb?param1=value1
        LOG.log(Level.FINEST, "getMethod(): {0}", request.getMethod()); // returns: GET
        LOG.log(Level.FINEST, "getAuthType(): {0}", request.getAuthType()); // returns: Domino
        LOG.log(Level.FINEST, "getHeader('Authorization'): {0}", request.getHeader("Authorization"));
        // LOG.log(Level.INFO, "03: " + request.getCharacterEncoding()); // returns:
        // LOG.log(Level.INFO, "04: " + request.getContentLength()); // returns:
        // LOG.log(Level.INFO, "05: " + request.getContentType()); // returns:
        // LOG.log(Level.INFO, "06: " + request.getContextPath()); // returns: /elDoc/beta/elDoc.nsf
        // LOG.log(Level.INFO, "07: " + request.getLocalAddr()); // returns: 10.4.0.84
        // LOG.log(Level.INFO, "08: " + request.getLocalName()); // returns: beta.eldoc.dev4.local
        // LOG.log(Level.INFO, "09: " + request.getLocalPort()); // returns: 80
        // LOG.log(Level.INFO, "10: " + request.getPathInfo()); // returns: /test/bbb
        // LOG.log(Level.INFO, "11: " + request.getPathTranslated()); // returns: /local/dominoData/elDoc.nsf/xRes.xsp/test/bbb
        // LOG.log(Level.INFO, "12: " + request.getProtocol()); // returns: HTTP/1.1
        // LOG.log(Level.INFO, "13: " + request.getQueryString()); // returns:
        // LOG.log(Level.INFO, "14: " + request.getScheme()); // returns: http
        // LOG.log(Level.INFO, "15: " + request.getServletPath()); // returns: /xRes.xsp
        // LOG.log(Level.INFO, "16: " + request.getAttributeNames()); // returns:
//		LOG.log(Level.INFO, "17: " + request.getParameterMap()); // returns:
        LOG.log(Level.FINEST, "getRequestURL(): {0}", request.getRequestURL()); // returns: http://beta.eldoc.dev4.local/elDoc/beta/elDoc.nsf/xRes.xsp/test/bbb
        LOG.log(Level.FINEST, "getUserPrincipal(): {0}", request.getUserPrincipal()); // returns: CN=elAdmin/O=DEV
        LOG.log(Level.FINEST, "getRemoteAddr(): {0}", request.getRemoteAddr()); // returns: IP address of remote host
    }


    private static void debugRequestHeaders(HttpServletRequest request) {
        LOG.log(Level.FINEST, "-------------------------------> HEADERS");
        Enumeration<String> e = request.getHeaderNames();
        while (e.hasMoreElements()) {
            String headerName = e.nextElement();
            LOG.log(Level.FINEST, "Header: {0} = ''{1}''", new Object[]{headerName, request.getHeader(headerName)});
        }
    }


    private static void debugRequestCookies(HttpServletRequest request) {
        LOG.log(Level.FINEST, "-------------------------------> COOKIES");
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                LOG.log(Level.FINEST, "cookie: {0} = ''{1}''", new Object[]{cookie.getName(), cookie.getValue()});
            }
        }
    }


    private static void debugRequestBody(HttpServletRequest request) {
        LOG.log(Level.FINEST, "-------------------------------> BODY");
        try {

            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader bufferedReader = null;

            try (InputStream inputStream = request.getInputStream();) {
                if (inputStream != null) {
                    bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    char[] charBuffer = new char[128];
                    int bytesRead = -1;
                    while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                        stringBuilder.append(charBuffer, 0, bytesRead);
                    }
                } else {
                    stringBuilder.append("");
                }
            } catch (IOException ex) {
                throw ex;
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }

            LOG.log(Level.FINEST, stringBuilder.toString());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error retrievnig the request Body", e);
        }
    }


    private void debugSaveToTemp(JsonObject callBackObject) throws IOException {
        String downloadUri = callBackObject.getString("url");
        URL url = new URL(downloadUri);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        InputStream stream = connection.getInputStream();
        File savedFile = File.createTempFile("oo", null);
        try (FileOutputStream out = new FileOutputStream(savedFile);) {
            int read;
            final byte[] bytes = new byte[1024];
            while ((read = stream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            out.flush();
            LOG.log(Level.INFO, ">> Saved as: {0} FileName: {1}", new Object[]{savedFile.getAbsolutePath(), savedFile.getName()});
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "", e);
        } finally {
            connection.disconnect();
        }
    }

    //
    // Getters & Setters --------------------------------------------------------------------------
    //
}
