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
package ai.eldoc.model;

import java.io.Serializable;
import java.time.ZoneId;
import java.util.*;


/**
 * Represents range of dates
 */
public final class DateRange implements Serializable {

    private static final long serialVersionUID = -4454261806211477884L;


    private static enum DAY {
        START,
        END
    }

    private Date _dateFrom;
    private Date _dateTo;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public DateRange() {
    }


    /**
     * Creates instance from Date List representing data range
     *
     * @param dateRangeList must contain exactly two Date items otherwise ignored (also ignored in case NULL)
     */
    public DateRange(List<Date> dateRangeList) {
        if (dateRangeList != null && dateRangeList.size() == 2) {
            setDateFrom(dateRangeList.get(0));
            setDateTo(dateRangeList.get(1));
        }
    }


    /**
     * Resets time to either 00:00:00.000 or 23:59:59.999 based on the given param
     *
     * @param date
     * @param zoneId
     * @param timeOfDay
     * @return
     */
    private static Date toDayStartOrEndInGivenTimeZone(Date date, ZoneId zoneId, DAY timeOfDay) {
        if (date == null) {
            return null;
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date.getTime());
        cal.setTimeZone(TimeZone.getTimeZone(zoneId));
        switch (timeOfDay) {
            case START:
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                return cal.getTime();
            case END:
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                return cal.getTime();
            default:
                throw new IllegalArgumentException("Default case is not valid");
        }
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Sets values from Date List representing data range
     *
     * @param dateRangeList must contain exactly two Date items otherwise ignored (also ignored in case NULL)
     */
    public void setFromDateRangeList(List<Date> dateRangeList) {
        if (dateRangeList != null && dateRangeList.size() == 2) {
            setDateFrom(dateRangeList.get(0));
            setDateTo(dateRangeList.get(1));
        } else {
            setDateFrom(null);
            setDateTo(null);
        }
    }


    /**
     * Returns Date List representing date range or null in case it was not set
     *
     * @return list representing date range or NULL
     */
    public List<Date> toDateRangeList() {
        if (_dateFrom == null || _dateTo == null) {
            return null;
        }
        return Arrays.asList(_dateFrom, _dateTo);
    }


    /**
     * Checks if the given list<?> represents a valid DataRange List
     *
     * @param dateRangeList
     * @return
     */
    public static boolean isValid(List dateRangeList) {
        return dateRangeList != null && dateRangeList.size() == 2 && dateRangeList.stream().allMatch(item -> item instanceof Date);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public Date getDateFrom() {
        return _dateFrom;
    }


    public void setDateFrom(Date dateFrom) {
        _dateFrom = dateFrom;
    }


    /**
     * Returns dateFrom UTC date with the time set to 23:59:59 in the given timezone
     *
     * @param zoneId
     * @return
     */
    public Date getDateFrom(ZoneId zoneId) {
        return toDayStartOrEndInGivenTimeZone(_dateFrom, zoneId, DAY.START);
    }


    /**
     * Sets dateFrom UTC date with the time set to 23:59:59 in the given timezone
     *
     * @param dateFrom
     * @param zoneId
     */
    public void setDateFrom(Date dateFrom, ZoneId zoneId) {
        _dateFrom = toDayStartOrEndInGivenTimeZone(dateFrom, zoneId, DAY.START);
    }


    public Date getDateTo() {
        return _dateTo;
    }


    public void setDateTo(Date dateTo) {
        _dateTo = dateTo;
    }


    /**
     * Returns dateTo UTC date with the time set to 23:59:59 in the given timezone
     *
     * @param zoneId
     * @return
     */
    public Date getDateTo(ZoneId zoneId) {
        return toDayStartOrEndInGivenTimeZone(_dateTo, zoneId, DAY.END);
    }


    /**
     * Sets dateTo UTC date with the time set to 23:59:59 in the given timezone
     *
     * @param dateTo
     * @param zoneId
     */
    public void setDateTo(Date dateTo, ZoneId zoneId) {
        _dateTo = toDayStartOrEndInGivenTimeZone(dateTo, zoneId, DAY.END);
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 47 * hash + Objects.hashCode(_dateFrom);
        hash = 47 * hash + Objects.hashCode(_dateTo);
        return hash;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DateRange other = (DateRange) obj;
        if (!Objects.equals(_dateFrom, other._dateFrom)) {
            return false;
        }
        if (!Objects.equals(_dateTo, other._dateTo)) {
            return false;
        }
        return true;
    }

}
