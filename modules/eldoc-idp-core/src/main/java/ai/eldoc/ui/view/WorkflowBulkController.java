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
package ai.eldoc.ui.view;

import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.context.UserContext;
import ai.eldoc.dao.BasicModelProcessor;
import ai.eldoc.dao.WorkflowDao;
import ai.eldoc.data.LazyQuery;
import ai.eldoc.data.Operation;
import ai.eldoc.model.LogExt;
import ai.eldoc.model.Workflow;
import ai.eldoc.model.directory.DirectoryItem;
import ai.eldoc.service.*;
import ai.eldoc.util.JsfUtils;
import jakarta.faces.annotation.RequestMap;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Strings;
import org.omnifaces.cdi.ViewScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Workflow bulk controller
 */
@Named
@ViewScoped
public class WorkflowBulkController implements Serializable {

    private static final long serialVersionUID = 1483174544360764767L;

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowBulkController.class);

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    @RequestMap
    private Map<String, Object> _requestMap;

    @Inject
    private UserContext _userContext;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private WorkflowListController _workflowListController;

    private boolean _bulkEditDocs;
    private boolean _applyToAllPages;
    private Operation _operationTags;
    private Operation _operationReaders;
    private List<String> _tags;
    private List<String> _docReaders;
    private int _bulkTotalCount;
    private final AtomicInteger _bulkProcessedCount = new AtomicInteger();


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void btnBulkEditDocs() {
        _applyToAllPages = (boolean) _requestMap.getOrDefault("bulkEditApplyToAllPages", false); // This must be defined first
        if (_applyToAllPages) {
            _bulkTotalCount = _workflowListController.getDataProvider().getRowCount();
        } else {
            _bulkTotalCount = _workflowListController.getSelectedDocs().size();
        }
        if (getTotalCount() == 0) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.noItemsSelected"));
            setBulkEditDocs(false);
            return;
        }
        setBulkEditDocs(true);
    }


    public void btnBulkEditCancel() {
        bulkEditReset();
    }


    public void btnExecuteBulkEdit() {
        executeBulkEdit();
        bulkEditReset();
    }


    public List<String> completeDocAclNames(String query) {
        List<String> result = new ArrayList<>();

        // Add 'All users' role
        _directoryService.queryRole(query, Set.of(DirectoryConstants.ROLE_ALLUSERS)).stream()
                .map(DirectoryItem::getIdSys)
                .forEach(result::add);

        return result;
    }


    private void bulkEditReset() {
        _operationTags = null;
        _operationReaders = null;
        _bulkEditDocs = false;
        _tags = null;
        _docReaders = null;
        _bulkProcessedCount.set(0);
        setBulkEditDocs(false);
    }


    private void executeBulkEdit() {
        boolean aclUpdateRequired = true;
        if ((_operationReaders == Operation.ADD || _operationReaders == Operation.REMOVE) && _docReaders.isEmpty()) {
            aclUpdateRequired = false;
        }
        boolean tagUpdateRequired = true;
        if ((_operationTags == Operation.ADD || _operationTags == Operation.REMOVE) && _tags.isEmpty()) {
            tagUpdateRequired = false;
        }

        if (!aclUpdateRequired && !tagUpdateRequired) {
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.bulk.noUpdatesRequired"));
            return;
        }

        if (_docReaders.contains(DirectoryConstants.ROLE_ALLUSERS) && (_operationReaders == Operation.SET || _operationReaders == Operation.ADD)) {
            _docReaders.clear();
            _docReaders.add(DirectoryConstants.ROLE_ALLUSERS);
            _operationReaders = Operation.SET;
        }

        if (_applyToAllPages) {
            BasicModelProcessor<Workflow> workflowProcessor = new BasicModelProcessor<>() {
                @Override
                public void process(Workflow workflow) {
                    bulkEditWorkflowApplyToItem(workflow);
                }
            };

            // Process records
            LazyQuery lq = _workflowListController.getDataProvider().getLazyQueryClone();
            lq.setPageSize(-1);
            lq.setFirst(-1);
            _workflowDao.processWithCursor(lq, workflowProcessor, _userContext.getAclContext());
        } else {
            _workflowListController.getSelectedDocs().forEach(this::bulkEditWorkflowApplyToItem);
        }

        if (getTotalCount() == _bulkProcessedCount.get()) {
            // All documents were processed
            JsfUtils.addInfoMessage(format(_localeContext.getLabel("msg.bulk.processedAll"), _bulkProcessedCount));
        } else {
            // Some documents were skipped
            JsfUtils.addWarnMessage(format(_localeContext.getLabel("msg.bulk.processedWithSkipped"), _bulkProcessedCount, _bulkTotalCount));
        }
    }


    private void bulkEditWorkflowApplyToItem(Workflow workflow) {
        try {
            Map<String, Object> updates = new HashMap<>();

            // Process Tags changes
            List<String> workflowTagsOld = workflow.getTags();
            List<String> workflowTagsNew = workflowTagsOld == null ? new ArrayList<>() : new ArrayList<>(workflowTagsOld);
            switch (_operationTags) {
                case SET:
                    workflowTagsNew = _tags;
                    break;
                case ADD:
                    workflowTagsNew.addAll(_tags);
                    break;
                case REMOVE:
                    workflowTagsNew.removeAll(workflow.clearTags(_tags));
                    break;
            }

            Set<String> oldTags = workflowTagsOld == null
                    ? Collections.emptySet()
                    : new HashSet<>(workflowTagsOld);

            Set<String> newTags = workflow.getTags() == null
                    ? Collections.emptySet()
                    : new HashSet<>(workflow.getTags());

            workflow.setTags(workflowTagsNew);
            if (!oldTags.equals(newTags)) {
                updates.put(Workflow._TAGS, workflow.getTags());
            }

            // Process access changes
            Set<String> oldAclR = new HashSet<>(workflow.getAcl().getR());
            Set<String> newDocReaders = new HashSet<>(_docReaders);
            switch (_operationReaders) {
                case SET:
                    workflow.getAcl().setR(newDocReaders);
                    break;
                case ADD:
                    workflow.getAcl().getR().addAll(newDocReaders);
                    break;
                case REMOVE:
                    workflow.getAcl().getR().removeAll(newDocReaders);
                    break;
            }

            // Ensure default ACL
            WorkflowService.ensureDefaultAcl(workflow);

            if (!CollectionUtils.isEqualCollection(oldAclR, workflow.getAcl().getR())) {
                updates.put(Workflow._PATH_ACL_R, workflow.getAcl().getR());
            }

            if (updates.isEmpty()) {
                // No updates required but we still mark this record as processed
                _bulkProcessedCount.incrementAndGet(); // Increment counter in case of successful update
                return;
            }

            if (_workflowDao.applyUpdates(null, workflow, updates)) {
                if (updates.containsKey(Workflow._PATH_ACL_R)) {
                    LogHelper.setEvent("id00327", null, null, new LogExt(workflow));
                }
                _bulkProcessedCount.incrementAndGet(); // Increment counter in case of successful update
            }
        } catch (Exception e) {
            LOG.error(String.valueOf(workflow.getId()), e);
        }
    }


    public List<String> completeTags(String query) {
        if (isBlank(query)) {
            return new ArrayList<>(_workflowDao.getTagsDistinct());
        } else {
            List<String> result = _workflowDao.getTagsDistinct()
                    .stream()
                    .filter(tag -> Strings.CI.contains(tag, query))
                    .collect(Collectors.toList());
            if (!Operation.REMOVE.equals(_operationTags)) {
                // For ADD and SET operations we allow to add non-existing tags
                // NOTE: by default _operationTags is null (default selection of the fist item ADD via UI), so it is also covered
                result.addFirst(query);
            }
            return result;
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public boolean isBulkEditDocs() {
        return _bulkEditDocs;
    }


    public void setBulkEditDocs(boolean bulkEditDocs) {
        _bulkEditDocs = bulkEditDocs;
    }


    public int getTotalCount() {
        return _bulkTotalCount;
    }


    public Operation getOperationTags() {
        return _operationTags;
    }


    public void setOperationTags(Operation operationTags) {
        _operationTags = operationTags;
    }


    public Operation getOperationReaders() {
        return _operationReaders;
    }


    public void setOperationReaders(Operation operationReaders) {
        _operationReaders = operationReaders;
    }


    public List<String> getTags() {
        return _tags;
    }


    public void setTags(List<String> tags) {
        _tags = tags;
    }


    public List<String> getDocReaders() {
        return _docReaders;
    }


    public void setDocReaders(List<String> docReaders) {
        _docReaders = docReaders;
    }
}
