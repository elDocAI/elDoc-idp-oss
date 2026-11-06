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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.model.*;
import ai.eldoc.util.JsonbUtils;
import ai.eldoc.util.RectangleUtils;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.apache.commons.lang3.Range;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.jspecify.annotations.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static java.text.MessageFormat.format;
import static java.util.Map.entry;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Model for storing Recognition Form configuration data used for processing documents (text-base and image-base)
 */
public class RecoForm implements BasicFileAwareModel, BasicMetaAwareModel, BasicAclAwareModel, BasicAuthorAwareModel, PropertyAwareModel, TagAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = -5842671929353235607L;


    public enum FILE_TAG {
        SOURCE,
        PREPROCESSED
    }

    public static final String AUTOMATIC_FORM_NAME = "Auto recognition";
    public static final String AUTOMATIC_FORM_ID = "000000000001100000000000";

    //
    // IMPORTANT: RecoForm Import API to be updated on property change
    //
    public static final String _TYPE = "type";  // stands for RecoForm type: 0 - AnchorBased, 1 - RegEx Based, etc.
    public static final String _NAME = "name";
    public static final String _DESCRIPTION = "dsc";
    public static final String _EXTRACTION_CONTEXT = "extrCtx";
    public static final String _EXTRACTION_MODEL = "extrMdl";
    public static final String _STATUS = "status";
    public static final String _USE_POSTPROC = "usePostProc";
    public static final String _POSTPROC = "postProc";
    public static final String _PAGES = "pages";
    public static final String _FORM = "form";
    public static final String _LANGS = "langs";

    public static final int NEW = 10;
    public static final int ACTIVE = 20;
    public static final int SUSPENDED = 30;

    public static final int TYPE_AIBASED = 2;

    //
    // RecoForm type names
    //
    public static final Map<Integer, String> TYPE_NAME_PREFIX = Map.ofEntries();

    public static final Map<Integer, String> TYPE_NAME_SUFFIX = Map.ofEntries(
            entry(TYPE_AIBASED, "AI/LLM based")
    );

    //
    // RecoForm type groups
    //
    public static final List<Integer> AUTO_GENERATED_FORMS = Stream.of(Set.of(TYPE_AIBASED)) // Add other types via comma
            .flatMap(Collection::stream)
            .toList();

    private String _id;
    private String _author;
    private String _name;
    private String _description;
    private String _extractionContext;
    private String _extractionModel;
    private int _type;
    private int _status;
    private String _form;
    private List<RecoPage> _pages;
    private boolean _usePostProc;
    private String _postProc;
    private Meta _meta;
    private Acl _acl;
    private List<DbFileRef> _files;
    private Map<String, Object> _properties;
    private List<String> _langs;
    private List<String> _tags;


    //
    // Constructor --------------------------------------------------------------------------------
    //


    /**
     * RecoForm Model internal constructor, mostly used for MongoDB POJO support only
     */
    protected RecoForm() {
        this(null, null, null);
    }


    /**
     * RecoForm Model main constructor
     *
     * @param authorSystemId author of the model
     * @param writeAccess Write-Access set of systemId of users, groups, orgunits, roles
     * @param readAccess Read-Access set of systemId of users, groups, orgunits, roles
     */
    public RecoForm(String authorSystemId, Set<String> writeAccess, Set<String> readAccess) {
        _meta = new Meta();
        _acl = new Acl();
        _pages = new ArrayList<>();
        _files = new ArrayList<>();
        _langs = new ArrayList<>();
        _properties = new TreeMap<>();
        _author = authorSystemId;
        _status = NEW;

        // Initialization of ACL
        if (writeAccess != null) {
            _acl.getW().addAll(writeAccess);
        }

        if (readAccess != null) {
            _acl.getR().addAll(readAccess);
        }
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @BsonIgnore
    @JsonbTransient
    public boolean isStatusNew() {
        return getStatus() == NEW;
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isStatusActive() {
        return getStatus() == ACTIVE;
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isStatusSuspended() {
        return getStatus() == SUSPENDED;
    }


    /**
     * @return {@code true} in case this RecoForm contains extendable fields, what means that the form can span across several pages,
     * or also means that some fields which are located under the extendable fields could float on the page
     */
    @BsonIgnore
    @JsonbTransient
    public boolean isExtendable() {
        return getFields().stream().anyMatch(RecoField::isExtendable);
    }


    /**
     * @return {@code true} if this RecoForm type is set to one of the auto-generated form types
     */
    @BsonIgnore
    @JsonbTransient
    public boolean isTypeAuto() {
        return AUTO_GENERATED_FORMS.contains(getType());
    }


    public String toJson() {
        return JsonbUtils.getInstance().toJson(this);
    }


    public static RecoForm fromJson(String recoFormJson) {
        return JsonbUtils.getInstance().fromJson(recoFormJson, RecoForm.class);
    }


    public static String getFormTypeName(int formType) {
        return TYPE_NAME_PREFIX.getOrDefault(formType, "").concat(TYPE_NAME_SUFFIX.getOrDefault(formType, ""));
    }


    //
    // Recognition related logic methods ----------------------------------------------------------
    //


    /**
     * Checks whether any page has any fields defined on the RecoForm
     *
     * @return {@code true} if at least one page has at least one field defined on the RecoForm
     */
    @BsonIgnore
    @JsonbTransient
    public boolean hasFields() {
        return !getPages().isEmpty() && !getFields().isEmpty();
    }


    /**
     * Finds a page of this form that contains the provided field
     *
     * @param recoField field to find page for
     * @return page of this form that contains the provided field, or {@code null} if field is not found
     */
    @BsonIgnore
    @JsonbTransient
    public RecoPage getPageByField(RecoField recoField) {
        return getPages().stream()
                .filter(page -> page.getFields().contains(recoField))
                .findFirst().orElse(null);
    }


    /**
     * Checks whether the given field is floating by checking whether any of the previous fields (sorted by ValueRegion)
     * is extendable. If yes, check whether ValueRegion of that extendable field is above (fully or partially)
     * AnchorRegion or ValueRegion of the given field
     *
     * @param recoField field to check
     * @return If the given field is floating, returns the extendable field, under which it is floating, otherwise {@code null}
     */
    @BsonIgnore
    @JsonbTransient
    public RecoField isFloatingFieldCheckAndGetExtendableField(RecoField recoField) {
        int fieldPageId = getPages().indexOf(getPageByField(recoField));
        for (int pageIdx = fieldPageId; pageIdx >= 0; pageIdx--) {
            RecoPage recoPage = getPages().get(pageIdx);

            RecoField extendableField = isFloatingFieldCheckAndGetExtendableField(recoPage, recoField);
            if (extendableField != null) {
                return extendableField;
            }

            if (recoPage.getProp().isApplyPageBreakBefore()) {
                break;
            }

            pageIdx = getFirstRelatedPageIndex(recoPage);
        }

        return null;
    }


    /**
     * Checks whether the given field is floating by checking whether any of the previous fields (sorted by ValueRegion)
     * is extendable. If yes, check whether ValueRegion of that extendable field is above (fully or partially)
     * AnchorRegion or ValueRegion of the given field
     *
     * @param recoPage page to check
     * @param recoField field to check
     * @return If the given field is floating, returns the extendable field, under which it is floating, otherwise {@code null}
     */
    @BsonIgnore
    @JsonbTransient
    private RecoField isFloatingFieldCheckAndGetExtendableField(RecoPage recoPage, RecoField recoField) {
        // Get list of fields arranged by ValueRegion
        List<RecoField> recoFieldsList = getFieldsArrangedByValueRect(recoPage);

        boolean samePage = recoFieldsList.contains(recoField);

        // Get the index of the given field
        int fieldIndex;
        if (samePage) {
            fieldIndex = recoFieldsList.indexOf(recoField);
        } else {
            fieldIndex = recoFieldsList.size();
        }

        if (fieldIndex < 0) {
            // This is actually an error as given field was not found in the field list due to some reason
            String msg = format("Field[{0}] is not found in the fields list", recoField.getName());
            throw new ElDocRuntimeException(msg);
        } else if (fieldIndex == 0) {
            // In case it is first field in the arranged list - it cannot be floating, thus end here
            return null;
        }

        // Get start X and end X coordinates of the anchor region
        double anchorRectStartY = recoField.getAnchorRegion().getY();
        Range<Double> anchorRectRange = RectangleUtils.getRangeX(recoField.getAnchorRegion(), true, false);

        // Get start X and end X coordinates of the value region
        Range<Double> valueRectRange = RectangleUtils.getRangeX(recoField.getValueRegion(), true, false);

        // Start iterating backward and check the previous fields
        for (int i = (fieldIndex - 1); i >= 0; i--) {
            RecoField previousRecoField = recoFieldsList.get(i);
            if (!previousRecoField.isExtendable()) {
                // In case this previous field is not extendable - then we proceed further with checks
                continue;
            }

            double prevFieldValueRectStartY = previousRecoField.getValueRegion().getY();
            Range<Double> prevFieldValueRectRange = RectangleUtils.getRangeX(previousRecoField.getValueRegion(), true, false);

            // Check whether anchor is under the value region of the expandable field
            boolean anchorYStartUnderValueRect = anchorRectStartY >= prevFieldValueRectStartY || !samePage;
            boolean anchorXUnderValueRect = anchorRectRange.isOverlappedBy(prevFieldValueRectRange);

            // Check whether value is under the value region of the expandable field
            boolean valueXUnderValueRect = valueRectRange.isOverlappedBy(prevFieldValueRectRange);

            // In case at least any part of the anchor or value field is under the extendable field ValueRegion - the current field is expandable
            if (anchorYStartUnderValueRect && anchorXUnderValueRect || valueXUnderValueRect) {
                return previousRecoField;
            }
        }
        return null;
    }


    /**
     * @return the first page of the RecoForm that contain fields
     */
    @BsonIgnore
    @JsonbTransient
    public int getFirstPageWithFields() {
        for (int i = 0; i < getPages().size(); i++) {
            if (!getPages().get(i).getFields().isEmpty()) {
                return i;
            }
        }
        return 0;
    }


    /**
     * @return the last page of the RecoForm that contains fields
     */
    @BsonIgnore
    @JsonbTransient
    public int getLastPageWithFields() {
        for (int i = getPages().size() - 1; i >= 0; i--) {
            if (!getPages().get(i).getFields().isEmpty()) {
                return i;
            }
        }
        return 0;
    }


    /**
     * @return fields from all RecoForm pages
     */
    @BsonIgnore
    @JsonbTransient
    public List<RecoField> getFields() {
        return getPages().stream().flatMap(page -> page.getFields().stream()).collect(Collectors.toList());
    }


    /**
     * Finds all fields on provided and extra (if present) pages, as defined by
     * {@link #getLastRelatedPageIndex(RecoPage)},
     * arranged by Anchor rect position (from top to bottom)
     *
     * @param recoPage page to find fields on
     * @return fields arranged by anchor rect position
     */
    @BsonIgnore
    @JsonbTransient
    public List<RecoField> getFieldsArrangedByAnchorRect(RecoPage recoPage) {
        List<RecoField> recoFields = new ArrayList<>();

        int firstRelatedPage = getFirstRelatedPageIndex(recoPage);
        int lastRelatedPage = getLastRelatedPageIndex(recoPage);

        for (int i = firstRelatedPage; i <= lastRelatedPage; i++) {

            // Add all fields from currently processed page, arranged by anchor rect
            recoFields.addAll(getPages().get(i).getFieldsArrangedByAnchorRect());
        }

        return recoFields;
    }


    /**
     * Finds all fields on provided and extra (if present) pages, as defined by
     * {@link #getLastRelatedPageIndex(RecoPage)},
     * arranged by Value rect position (from top to bottom)
     *
     * @param recoPage page to find fields on
     * @return fields arranged by value rect position
     */
    @BsonIgnore
    @JsonbTransient
    public List<RecoField> getFieldsArrangedByValueRect(RecoPage recoPage) {
        List<RecoField> recoFields = new ArrayList<>();

        int firstRelatedPage = getFirstRelatedPageIndex(recoPage);
        int lastRelatedPage = getLastRelatedPageIndex(recoPage);

        for (int i = firstRelatedPage; i <= lastRelatedPage; i++) {

            // Add all fields from currently processed page, arranged by value rect
            recoFields.addAll(getPages().get(i).getFieldsArrangedByValueRect());
        }

        return recoFields;
    }


    /**
     * Finds index of the first page, that is treated as an 'extra' page on this form, starting from the requested page.<br>
     * Page is treated as an extra page if there is any page before it, which extendable field
     * refers to it or any of the later pages.
     *
     * @param recoPage page to find a related page index for
     * @return index of the first related page
     */
    public int getFirstRelatedPageIndex(RecoPage recoPage) {
        for (int i = 0; i <= recoPage.getPageIndex(); i++) {
            if (getLastRelatedPageIndex(getPages().get(i)) >= recoPage.getPageIndex()) {
                return i;
            }
        }

        return recoPage.getPageIndex();
    }


    /**
     * Finds index of the last page, that is treated as an 'extra' page on this form, starting from the requested page.<br>
     * Page is treated as an extra page if there is any page before it, which extendable field
     * refers to it or any of the later pages.
     *
     * @param recoPage page to find a related page index for
     * @return index of the last related page
     */
    public int getLastRelatedPageIndex(RecoPage recoPage) {

        // Find last page, referenced from AnchorUnderTable of fields on this page
        int lastRelatedPage = recoPage.getPageIndex();

        for (int i = recoPage.getPageIndex(); i <= lastRelatedPage; i++) {
            // Find last page, referenced from currently processed page
            int currentLastRelatedPage = getPages().get(i).getFields().stream()
                    .filter(RecoField::isTypeTable) // Process only table fields
                    .filter(rf -> rf.getTableProp().getAnchorUnderTablePageIndex() != 0) // If AnchorUnderTablePageIndex is 0 - default logic applies, not marking this page as 'extra'
                    .mapToInt(rf -> rf.getTableProp().getAnchorUnderTablePageIndex())
                    .findFirst()
                    .orElse(getPages().get(i).getPageIndex());

            // If current page refers to another page even further than already detected bound - remember new bound
            if (currentLastRelatedPage > lastRelatedPage) {
                lastRelatedPage = currentLastRelatedPage;
            }
        }

        return lastRelatedPage;
    }


    /**
     * Finds RecoPage, that contains Header/Footer settings, related to the provided page<br>
     * In case one of the previous pages has properties configured and {@link PageProperties#isApplyHeaderFooterRemainingPages() }
     * property set - such a page will be returned. In all other cases the provided page is returned.
     *
     * @param recoPage Page for which to find the header/footer settings holder
     * @return RecoPage containing header/footer settings, or the provided page if none found
     */
    @BsonIgnore
    @JsonbTransient
    public RecoPage getHeaderFooterSettingsHolderForPage(RecoPage recoPage) {
        for (int pageIdx = 0; pageIdx < getPages().indexOf(recoPage); pageIdx++) {
            if (getPages().get(pageIdx).getProp().isApplyHeaderFooterRemainingPages()) {
                return getPages().get(pageIdx);
            }
        }
        return recoPage;
    }


    /**
     * Finds RecoPage properties that contain Header/Footer settings related to the provided page<br>
     * In case one of the previous pages has properties configured and {@link PageProperties#isApplyHeaderFooterRemainingPages() }
     * property set - such page properties will be returned. In all other cases the provided page is returned.
     *
     * @param recoPage Page for which to find the header/footer settings holder
     * @return PageProperties containing header/footer settings, or the provided page properties if none found
     */
    @BsonIgnore
    @JsonbTransient
    public PageProperties getHeaderFooterSettingsForPage(RecoPage recoPage) {
        return getHeaderFooterSettingsHolderForPage(recoPage).getProp();
    }


    @BsonIgnore
    @JsonbTransient
    public RecoField getFieldByTag(String tag) {
        return getFields().stream().filter(field -> field.getTags().contains(tag)).findFirst().orElse(null);
    }


    /**
     * @return Accumulated tags from all pages (distinct values only for all pages)
     */
    @BsonIgnore
    @JsonbTransient
    public List<String> getKeywords() {
        return getPages().stream()
                .flatMap(page -> page.getKeywords().stream())
                .distinct().toList();
    }


    /**
     * @return Inclusive keywords
     */
    @BsonIgnore
    @JsonbTransient
    public List<String> getKeywordsInclusive() {
        return getPages().stream()
                .flatMap(page -> page.getKeywords().stream().filter(keyword -> !keyword.startsWith("-")))
                .distinct()
                .toList();
    }


    /**
     * @return Minus keywords (and also removes leading minus from them)
     */
    @BsonIgnore
    @JsonbTransient
    public List<String> getKeywordsMinus() {
        return getPages().stream()
                .flatMap(page -> page.getKeywords().stream().filter(keyword -> keyword.startsWith("-")).map(minusKeyword -> minusKeyword.substring(1)))
                .distinct()
                .toList();
    }


    /**
     * Sets keywords on the 1st page only (workaround)<br>
     * IMPORTANT: before adding keywords - make sure that at least one (first) RecoPage were already added
     *
     * @param keywords keywords to be set
     * @throws IllegalStateException when no RecoPage were added before adding keywords
     */
    @BsonIgnore
    @JsonbTransient
    public void setKeywords(List<String> keywords) throws IllegalStateException {
        if (getPages().isEmpty()) {
            throw new IllegalStateException("No RecoPage present, add RecoPage first");
        }
        getPages().getFirst().setKeywords(keywords);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getName() {
        return _name;
    }


    public void setName(String name) {
        _name = name;
    }


    @BsonProperty(_DESCRIPTION)
    @JsonbProperty(_DESCRIPTION)
    public String getDescription() {
        return _description;
    }


    @BsonProperty(_DESCRIPTION)
    @JsonbProperty(_DESCRIPTION)
    public void setDescription(String description) {
        _description = description;
    }


    @BsonProperty(_EXTRACTION_CONTEXT)
    @JsonbProperty(_EXTRACTION_CONTEXT)
    public String getExtractionContext() {
        return _extractionContext;
    }


    @BsonProperty(_EXTRACTION_CONTEXT)
    @JsonbProperty(_EXTRACTION_CONTEXT)
    public void setExtractionContext(String extractionContext) {
        _extractionContext = extractionContext;
    }


    @BsonProperty(_EXTRACTION_MODEL)
    @JsonbProperty(_EXTRACTION_MODEL)
    public String getExtractionModel() {
        return _extractionModel;
    }


    @BsonProperty(_EXTRACTION_MODEL)
    @JsonbProperty(_EXTRACTION_MODEL)
    public void setExtractionModel(String extractionModel) {
        _extractionModel = extractionModel;
    }


    public int getType() {
        return _type;
    }


    public void setType(int type) {
        _type = type;
    }


    /**
     * @return all Pages defined on the RecoForm
     */
    public List<RecoPage> getPages() {
        return _pages;
    }


    public void setPages(List<RecoPage> pages) {
        _pages.clear();
        if (pages != null && !pages.isEmpty()) {
            _pages.addAll(pages);
        }
    }


    public int getStatus() {
        return _status;
    }


    public void setStatus(int status) {
        _status = status;
    }


    @BsonIgnore
    @JsonbTransient
    public DbFileRef getSourceFile() {
        return getFileByTag(FILE_TAG.SOURCE.name());
    }


    @BsonIgnore
    @JsonbTransient
    public DbFileRef getPreprocessedFile() {
        return getFileByTag(FILE_TAG.PREPROCESSED.name());
    }


    /**
     * Returns _id_web of the FormDefinition in case one assigned
     *
     * @return _id_web of FormDefinition
     */
    public String getForm() {
        return _form;
    }


    /**
     * Sets _id_web of the assigned FormDefinition
     *
     * @param form _id_web of the FormDefinition
     */
    public void setForm(String form) {
        _form = form;
    }


    public boolean isUsePostProc() {
        return _usePostProc;
    }


    public void setUsePostProc(boolean usePostproc) {
        _usePostProc = usePostproc;
    }


    public String getPostProc() {
        return _postProc;
    }


    public void setPostProc(String postProc) {
        _postProc = postProc;
    }


    @BsonIgnore
    @JsonbTransient
    public String getPostProcDecoded() throws ParseException {
        if (isBlank(getPostProc())) {
            return "";
        }
        return new String(Base64.getDecoder().decode(getPostProc()), StandardCharsets.UTF_8);
    }


    /**
     * @return List of enabled languages
     */
    public List<String> getLangs() {
        return _langs;
    }


    /**
     * Sets languages (only distinct values are to be stored)
     *
     * @param langs languages to be set
     */
    public void setLangs(List<String> langs) {
        _langs.clear();
        if (langs != null) {
            _langs.addAll(langs.stream().distinct().toList());
        }
    }


    //
    // Getters & Setters overrides ----------------------------------------------------------------
    //
    @Override
    @BsonProperty(_ID)
    @JsonbProperty(_ID)
    public String getId() {
        return _id;
    }


    @BsonProperty(_ID)
    @JsonbProperty(_ID)
    public void setId(String id) {
        _id = id;
    }


    @Override
    public String getAuthor() {
        return _author;
    }


    public void setAuthor(String author) {
        _author = author;
    }


    @Override
    @BsonProperty(_FILES)
    @JsonbProperty(_FILES)
    public List<DbFileRef> getFiles() {
        return _files;
    }


    @BsonProperty(_FILES)
    @JsonbProperty(_FILES)
    public void setFiles(List<DbFileRef> files) {
        _files.clear();
        if (files != null && !files.isEmpty()) {
            _files.addAll(files);
        }
    }


    @Override
    @BsonProperty(_META)
    @JsonbProperty(_META)
    public Meta getMeta() {
        return _meta;
    }


    @BsonProperty(_META)
//    @JsonbProperty(_META)
    @JsonbTransient // Json-B should not deserialize setter, as old recoForm exports contain wrong date format (i.e., Jun 11, 2021 12:14:22 PM)
    public void setMeta(Meta meta) {
        _meta = meta;
    }


    @Override
    @BsonProperty(_ACL)
    @JsonbProperty(_ACL)
    public Acl getAcl() {
        return _acl;
    }


    @BsonProperty(_ACL)
    @JsonbProperty(_ACL)
    public void setAcl(Acl acl) {
        _acl = acl;
    }


    @Override
    @BsonProperty(_PROPERTIES)
    @JsonbProperty(_PROPERTIES)
    public Map<String, Object> getProperties() {
        return _properties;
    }


    @BsonProperty(_PROPERTIES)
    @JsonbProperty(_PROPERTIES)
    public void setProperties(Map<String, Object> properties) {
        _properties.clear();
        if (properties != null && !properties.isEmpty()) {
            _properties.putAll(properties);
        }
    }


    @Override
    @Nullable
    @BsonProperty(_TAGS)
    @JsonbProperty(_TAGS)
    public List<String> getTags() {
        return _tags;
    }


    /**
     * Sets only cleared unique tags
     *
     * @param tags tags to be set
     */
    @BsonProperty(_TAGS)
    @JsonbProperty(_TAGS)
    public void setTags(@Nullable List<String> tags) {
        _tags = normalizeTags(tags);
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RecoForm recoForm = (RecoForm) o;
        return _type == recoForm._type
                && _status == recoForm._status
                && _usePostProc == recoForm._usePostProc
                && Objects.equals(_id, recoForm._id)
                && Objects.equals(_author, recoForm._author)
                && Objects.equals(_name, recoForm._name)
                && Objects.equals(_description, recoForm._description)
                && Objects.equals(_extractionContext, recoForm._extractionContext)
                && Objects.equals(_extractionModel, recoForm._extractionModel)
                && Objects.equals(_form, recoForm._form)
                && Objects.equals(_pages, recoForm._pages)
                && Objects.equals(_postProc, recoForm._postProc)
                && Objects.equals(_meta, recoForm._meta)
                && Objects.equals(_acl, recoForm._acl)
                && Objects.equals(_files, recoForm._files)
                && Objects.equals(_properties, recoForm._properties)
                && Objects.equals(_langs, recoForm._langs)
                && Objects.equals(_tags, recoForm._tags);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_id, _author, _name, _description, _extractionContext, _extractionModel, _type, _status, _form, _pages, _usePostProc, _postProc, _meta, _acl, _files, _properties, _langs, _tags);
    }
}
