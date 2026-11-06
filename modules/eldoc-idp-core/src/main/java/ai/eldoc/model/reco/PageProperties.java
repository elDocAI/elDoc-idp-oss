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
package ai.eldoc.model.reco;

import ai.eldoc.model.PropertyAwareModel;
import java.io.Serializable;
import java.util.*;


/**
 * Recognition Page settings (general recoPage properties)
 */
public class PageProperties implements Serializable {

    private static final long serialVersionUID = 2601080238299009037L;

    private static final String PG_APPLY_PAGE_BREAK_BEFORE = "pgApplyPgBrkBfr";
    private static final String PG_HEADER_ENABLED = "pgHdrEnbld";
    private static final String PG_HEADER_HEIGHT = "pgHdrHght";
    private static final String PG_HEADER_KEYWORDS = "pgHdrKwds";
    private static final String PG_HEADER_KEYWORDS_AS_BORDER = "pgHdrKwdsBrdr";
    private static final String PG_HEADER_AUTO = "pgHdrAuto";
    private static final String PG_FOOTER_ENABLED = "pgFtrEnbld";
    private static final String PG_FOOTER_HEIGHT = "pgFtrHght";
    private static final String PG_FOOTER_KEYWORDS = "pgFtrKwds";
    private static final String PG_FOOTER_KEYWORDS_AS_BORDER = "pgFtrKwdsBrdr";
    private static final String PG_FOOTER_AUTO = "pgFtrAuto";
    private static final String PG_APPLY_HEADER_FOOTER_REMAINING_PAGES = "pgHdrFtrAplRemPg";

    private static final List<String> PROP_HEADER_FOOTER = Arrays.asList(
            PG_HEADER_ENABLED, PG_HEADER_HEIGHT, PG_HEADER_KEYWORDS, PG_HEADER_KEYWORDS_AS_BORDER, PG_HEADER_AUTO, PG_FOOTER_ENABLED, PG_FOOTER_HEIGHT, PG_FOOTER_KEYWORDS,
            PG_FOOTER_KEYWORDS_AS_BORDER, PG_FOOTER_AUTO, PG_APPLY_HEADER_FOOTER_REMAINING_PAGES
    );

    private static final List<String> PROP_ALL = Arrays.asList(
            PG_APPLY_PAGE_BREAK_BEFORE, PG_HEADER_ENABLED, PG_HEADER_HEIGHT, PG_HEADER_KEYWORDS, PG_HEADER_KEYWORDS_AS_BORDER, PG_HEADER_AUTO, PG_FOOTER_ENABLED, PG_FOOTER_HEIGHT,
            PG_FOOTER_KEYWORDS, PG_FOOTER_KEYWORDS_AS_BORDER, PG_FOOTER_AUTO, PG_APPLY_HEADER_FOOTER_REMAINING_PAGES
    );

    private final PropertyAwareModel _recoPage;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public PageProperties(PropertyAwareModel recoPage) {
        _recoPage = recoPage;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Clears all properties
     * IMPORTANT: to be used with care!
     */
    void clear() {
        PROP_ALL.forEach(propName -> _recoPage.getProperties().remove(propName));
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public boolean isApplyPageBreakBefore() {
        return _recoPage.getProperty(PG_APPLY_PAGE_BREAK_BEFORE, false);
    }


    public void setApplyPageBreakBefore(boolean startsNewPage) {
        if (startsNewPage == false) {
            // Do not store default values
            _recoPage.removeProperty(PG_APPLY_PAGE_BREAK_BEFORE);
            return;
        }
        _recoPage.setProperty(PG_APPLY_PAGE_BREAK_BEFORE, startsNewPage);
    }


    public boolean isHeaderEnabled() {
        return _recoPage.getProperty(PG_HEADER_ENABLED, false);
    }


    public void setHeaderEnabled(boolean enabled) {
        if (enabled == false) {
            // Do not store default values
            _recoPage.removeProperty(PG_HEADER_ENABLED);
            return;
        }
        _recoPage.setProperty(PG_HEADER_ENABLED, enabled);
    }


    public int getHeaderHeight() {
        return _recoPage.getProperty(PG_HEADER_HEIGHT, 0);
    }


    public void setHeaderHeight(int height) {
        if (height == 0) {
            // Do not store default values
            _recoPage.removeProperty(PG_HEADER_HEIGHT);
            return;
        }
        _recoPage.setProperty(PG_HEADER_HEIGHT, height);
    }


    public List<String> getHeaderKeywords() {
        return _recoPage.getProperty(PG_HEADER_KEYWORDS, new ArrayList<>());
    }


    public void setHeaderKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            // Do not store default values
            _recoPage.removeProperty(PG_HEADER_KEYWORDS);
            return;
        }
        _recoPage.setProperty(PG_HEADER_KEYWORDS, keywords);
    }


    public boolean isHeaderUseKeywordsAsBorder() {
        return _recoPage.getProperty(PG_HEADER_KEYWORDS_AS_BORDER, false);
    }


    public void setHeaderUseKeywordsAsBorder(boolean useKeywordsAsBorder) {
        if (useKeywordsAsBorder == false) {
            // Do not store default values
            _recoPage.removeProperty(PG_HEADER_KEYWORDS_AS_BORDER);
            return;
        }
        _recoPage.setProperty(PG_HEADER_KEYWORDS_AS_BORDER, useKeywordsAsBorder);
    }


    public boolean isHeaderAuto() {
        return _recoPage.getProperty(PG_HEADER_AUTO, false);
    }


    public void setHeaderAuto(boolean auto) {
        if (auto == false) {
            // Do not store default values
            _recoPage.removeProperty(PG_HEADER_AUTO);
            return;
        }
        _recoPage.setProperty(PG_HEADER_AUTO, auto);
    }


    public boolean isFooterEnabled() {
        return _recoPage.getProperty(PG_FOOTER_ENABLED, false);
    }


    public void setFooterEnabled(boolean enabled) {
        if (enabled == false) {
            // Do not store default values
            _recoPage.removeProperty(PG_FOOTER_ENABLED);
            return;
        }
        _recoPage.setProperty(PG_FOOTER_ENABLED, enabled);
    }


    public int getFooterHeight() {
        return _recoPage.getProperty(PG_FOOTER_HEIGHT, 0);
    }


    public void setFooterHeight(int height) {
        if (height == 0) {
            // Do not store default values
            _recoPage.removeProperty(PG_FOOTER_HEIGHT);
            return;
        }
        _recoPage.setProperty(PG_FOOTER_HEIGHT, height);
    }


    public List<String> getFooterKeywords() {
        return _recoPage.getProperty(PG_FOOTER_KEYWORDS, new ArrayList<>());
    }


    public void setFooterKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            // Do not store default values
            _recoPage.removeProperty(PG_FOOTER_KEYWORDS);
            return;
        }
        _recoPage.setProperty(PG_FOOTER_KEYWORDS, keywords);
    }


    public boolean isFooterUseKeywordsAsBorder() {
        return _recoPage.getProperty(PG_FOOTER_KEYWORDS_AS_BORDER, false);
    }


    public void setFooterUseKeywordsAsBorder(boolean useKeywordsAsBorder) {
        if (useKeywordsAsBorder == false) {
            // Do not store default values
            _recoPage.removeProperty(PG_FOOTER_KEYWORDS_AS_BORDER);
            return;
        }
        _recoPage.setProperty(PG_FOOTER_KEYWORDS_AS_BORDER, useKeywordsAsBorder);
    }


    public boolean isFooterAuto() {
        return _recoPage.getProperty(PG_FOOTER_AUTO, false);
    }


    public void setFooterAuto(boolean auto) {
        if (auto == false) {
            // Do not store default values
            _recoPage.removeProperty(PG_FOOTER_AUTO);
            return;
        }
        _recoPage.setProperty(PG_FOOTER_AUTO, auto);
    }


    public boolean isApplyHeaderFooterRemainingPages() {
        return _recoPage.getProperty(PG_APPLY_HEADER_FOOTER_REMAINING_PAGES, false);
    }


    public void setApplyHeaderFooterRemainingPages(boolean applySame) {
        if (applySame == false) {
            // Do not store default values
            _recoPage.removeProperty(PG_APPLY_HEADER_FOOTER_REMAINING_PAGES);
            return;
        }
        _recoPage.setProperty(PG_APPLY_HEADER_FOOTER_REMAINING_PAGES, applySame);
    }


    /**
     * Checks, if this RecoPage properties have any properties, related to Header or Footer, set
     *
     * @return
     */
    public boolean isHeaderFooterDefined() {
        return PROP_HEADER_FOOTER.stream().anyMatch(_recoPage.getProperties()::containsKey);
    }


    @Override
    public int hashCode() {
        int hash = 5;
        hash = 41 * hash + Objects.hashCode(isApplyPageBreakBefore());
        hash = 41 * hash + Objects.hashCode(isHeaderEnabled());
        hash = 41 * hash + Objects.hashCode(getHeaderHeight());
        hash = 41 * hash + Objects.hashCode(getHeaderKeywords());
        hash = 41 * hash + Objects.hashCode(isHeaderUseKeywordsAsBorder());
        hash = 41 * hash + Objects.hashCode(isHeaderAuto());
        hash = 41 * hash + Objects.hashCode(isFooterEnabled());
        hash = 41 * hash + Objects.hashCode(getFooterHeight());
        hash = 41 * hash + Objects.hashCode(getFooterKeywords());
        hash = 41 * hash + Objects.hashCode(isFooterUseKeywordsAsBorder());
        hash = 41 * hash + Objects.hashCode(isFooterAuto());
        hash = 41 * hash + Objects.hashCode(isApplyHeaderFooterRemainingPages());
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
        final PageProperties other = (PageProperties) obj;
        if (!Objects.equals(isApplyPageBreakBefore(), other.isApplyPageBreakBefore())) {
            return false;
        }
        if (!Objects.equals(isHeaderEnabled(), other.isHeaderEnabled())) {
            return false;
        }
        if (!Objects.equals(getHeaderHeight(), other.getHeaderHeight())) {
            return false;
        }
        if (!Objects.equals(getHeaderKeywords(), other.getHeaderKeywords())) {
            return false;
        }
        if (!Objects.equals(isHeaderUseKeywordsAsBorder(), other.isHeaderUseKeywordsAsBorder())) {
            return false;
        }
        if (!Objects.equals(isHeaderAuto(), other.isHeaderAuto())) {
            return false;
        }
        if (!Objects.equals(isFooterEnabled(), other.isFooterEnabled())) {
            return false;
        }
        if (!Objects.equals(getFooterHeight(), other.getFooterHeight())) {
            return false;
        }
        if (!Objects.equals(getFooterKeywords(), other.getFooterKeywords())) {
            return false;
        }
        if (!Objects.equals(isFooterUseKeywordsAsBorder(), other.isFooterUseKeywordsAsBorder())) {
            return false;
        }
        if (!Objects.equals(isFooterAuto(), other.isFooterAuto())) {
            return false;
        }
        if (!Objects.equals(isApplyHeaderFooterRemainingPages(), other.isApplyHeaderFooterRemainingPages())) {
            return false;
        }
        return true;
    }

}
