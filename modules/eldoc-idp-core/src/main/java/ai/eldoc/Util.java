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

import io.seruco.encoding.base62.Base62;
import jakarta.faces.model.SelectItem;
import org.apache.commons.codec.binary.Base32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.naming.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;


/**
 * Utilities Class
 */
public final class Util {

    private static final Logger LOG = LoggerFactory.getLogger(Util.class);

    private static final Pattern EMAIL = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);


    //
    // Constructors -------------------------------------------------------------------------------
    //
    private Util() {
    }


    //
    // UUID related methods -----------------------------------------------------------------------
    //


    /**
     * Converts UUID to byteArray
     *
     * @param uuid
     * @return
     */
    public static byte[] uuidToByteArray(UUID uuid) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(16);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        return byteBuffer.array();
    }


    /**
     * Converts byteArray representing UUID to UUID
     *
     * @param byteArray
     * @return
     */
    public static UUID byteArrayToUUID(byte[] byteArray) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
        long mostSigBits = byteBuffer.getLong();
        long leastSigBits = byteBuffer.getLong();
        return new UUID(mostSigBits, leastSigBits);
    }


    /**
     * Converts UUID to ByteArray with Base62-encoding
     *
     * @param uuid
     * @return
     */
    public static byte[] uuidToBase62ByteArray(UUID uuid) {
        return Base62.createInstance().encode(Util.uuidToByteArray(uuid));
    }


    /**
     * Converts UUID to Base62 representing string
     *
     * @param uuid
     * @return
     */
    public static String uuidToBase62String(UUID uuid) {
        return new String(uuidToBase62ByteArray(uuid), StandardCharsets.US_ASCII);
    }


    /**
     * Converts UUID from Base62 representing string
     *
     * @param uuidBase62String
     * @return
     */
    public static UUID uuidFromBase62String(String uuidBase62String) {
        return Util.byteArrayToUUID(Base62.createInstance().decode(uuidBase62String.getBytes(StandardCharsets.US_ASCII)));
    }


    /**
     * Converts UUID to Base32 representing string (without padding)
     *
     * @param uuid
     * @return
     */
    public static String uuidToBase32String(UUID uuid) {
        String encoded = new Base32().encodeToString(Util.uuidToByteArray(uuid));
        encoded = encoded.substring(0, encoded.indexOf("=")); // Remove optional padding
        return encoded;
    }


    /**
     * Converts UUID from Base32 representing string
     *
     * @param uuidBase32String
     * @return
     */
    public static UUID uuidFromBase32String(String uuidBase32String) {
        return Util.byteArrayToUUID(new Base32().decode(uuidBase32String));
    }


    //
    // Collections related methods ----------------------------------------------------------------
    //
    public static boolean hasIntersection(Collection<String> collection1, Collection<String> collection2) {
        if (collection1 != null && collection2 != null) {
            return collection1.stream().anyMatch(collection2::contains);
        }
        return false;
    }


    //
    // Date & Time related methods ----------------------------------------------------------------
    //
    public static Date getDateWithoutTime(Date date) {
        if (date == null) {
            return null;
        }

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        return cal.getTime();
    }


    /**
     * Returns time period in the following format: Xd Yh Zm
     *
     * @param dateFrom
     * @param dateTo
     * @return
     */
    public static String formatPeriod(Date dateFrom, Date dateTo) {
        StringBuilder sb = new StringBuilder();
        if (dateFrom != null && dateTo != null) {
            LocalDateTime localDateFrom = dateFrom.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime localDateTo = dateTo.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            long days = ChronoUnit.DAYS.between(localDateFrom, localDateTo);
            long hours = ChronoUnit.HOURS.between(localDateFrom, localDateTo);
            long minutes = ChronoUnit.MINUTES.between(localDateFrom, localDateTo);

            hours = hours % 24;
            minutes = minutes % 60;

            if (days > 0) {
                sb.append(days).append("d ");
            }

            if (hours > 0) {
                sb.append(hours).append("h ");
            }

            if (minutes >= 0) {
                sb.append(minutes).append("m");
            }
        }

        return sb.toString();
    }


    //
    // Misc utilities methods ---------------------------------------------------------------------
    //
    public static SelectItem[] getSelectItems(List<?> entities, boolean selectOne) {
        int size = selectOne ? entities.size() + 1 : entities.size();
        SelectItem[] items = new SelectItem[size];
        int i = 0;
        if (selectOne) {
            items[0] = new SelectItem("", "---");
            i++;
        }
        for (Object x : entities) {
            items[i++] = new SelectItem(x, x.toString());
        }
        return items;
    }


    /**
     * Validates email address
     *
     * @param email
     * @return
     */
    public static boolean isValidEmailAddress(String email) {
        return email != null
                && email.length() <= 254
                && EMAIL.matcher(email).matches();
    }


    /**
     * Returns the last element from the given iterator
     *
     * @param <T>
     * @param iterator
     * @return
     */
    public static <T> T getLastElement(Iterator<T> iterator) {
        T lastElement = null;
        while (iterator.hasNext()) {
            lastElement = iterator.next();
        }
        return lastElement;
    }


    /**
     * Lists Context values<br>
     * <p>
     * See more: https://stackoverflow.com/questions/2324937/code-to-list-all-the-entries-in-jndi-on-remote-machine
     *
     * @param ctx
     * @return
     * @throws NamingException
     */
    public static Map contextToMap(Context ctx) throws NamingException {
        HashMap<String, Object> map = new HashMap<>();

        LOG.trace(">> Context class: '{}'", ctx.getClass().getName());
        String namespace = ctx instanceof InitialContext ? ctx.getNameInNamespace() : "";
        LOG.trace(">> Listing namespace: '{}'", namespace);

        NamingEnumeration<NameClassPair> list = ctx.list(namespace);
        while (list.hasMoreElements()) {
            NameClassPair next = list.next();
            String name = next.getName();
            String jndiPath = namespace + name;
            Object lookup;
            try {
                LOG.info(">> Looking up name: {}", jndiPath);
                Object tmp = ctx.lookup(jndiPath);
                if (tmp instanceof Context) {
                    lookup = contextToMap((Context) tmp);
                } else {
                    lookup = tmp.toString();
                }
            } catch (Throwable t) {
                lookup = t.getMessage();
            }
            map.put(name, lookup);
        }

        return map;
    }
}
