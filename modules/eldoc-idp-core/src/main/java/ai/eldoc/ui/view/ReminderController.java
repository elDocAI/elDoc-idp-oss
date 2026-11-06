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

import ai.eldoc.context.LocaleContext;
import ai.eldoc.context.UserContext;
import ai.eldoc.dao.*;
import ai.eldoc.data.MongoLazyDataModel;
import ai.eldoc.model.*;
import ai.eldoc.service.LogHelper;
import ai.eldoc.util.JsfUtils;
import ai.eldoc.util.LabelUtils;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.validator.ValidatorException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.lang3.Strings;
import org.omnifaces.cdi.ViewScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Reminders Controller
 */
@Named
@ViewScoped
public class ReminderController implements Serializable {

    @Serial
    private static final long serialVersionUID = -3156856536548735841L;

    private static final Logger LOG = LoggerFactory.getLogger(ReminderController.class);

    public static final String BEAN_NAME = "reminderController";
    public static final String COMPONENT_RENDERED = "uiReminderControl_rendered";

    @Inject
    private ReminderDao _reminderDao;

    @Inject
    private UserContext _userContext;

    @Inject
    private LocaleContext _localeContext;

    private String _refId; // docId that currently active
    private String _btnId;
    private Reminder _reminder;
    MongoLazyDataModel<Reminder> _reminderLazyDS;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Enables Exec Builder dialog
     *
     * @param ev
     */
    public void enableReminderDialog(jakarta.faces.event.ComponentSystemEvent ev) {
        if (!JsfUtils.getFacesContext().isPostback()) {
            JsfUtils.getViewMap().put(COMPONENT_RENDERED, true);
        }
    }


    /**
     * Returns reminder button style class
     *
     * @param docId
     * @return
     */
    public String getBtnRemiderStyleClass(String docId) {
        if (_reminderDao.hasAnyByRefId(_userContext.getAclContext(), docId)) {
            return "p-button-info";
        }
        return "p-button-primary p-button-outlined";
    }


    public void btnReminderActionListener(ActionEvent event) {
        // in order to update specific reminder button when there are several of them on the UI
        // we are passing the pressed EventHandler UIcomponent and retrieving from it
        // pressed button client id
        _btnId = event.getComponent().getClientId();
    }


    public <M extends BasicModel> void btnReminderDialogOpen(M model) {
        _refId = model.getId();
        List<Reminder> userReminders = _reminderDao.findAllByRefId(_userContext.getAclContext(), model.getId(), true);
        if (userReminders.isEmpty()) {
            _reminder = new Reminder(_userContext.getId());
            _reminder.setRefId(model.getId());
            _reminder.setRefType(model.getClass().getSimpleName());
            _reminder.setRecipients(List.of(_userContext.getId()));
        } else {
            _reminder = userReminders.getFirst();
        }
    }


    public void btnOk() {
        try {
            if (!_reminderDao.updateOrCreate(null, _reminder)) {
                LogHelper.setEvent("id00222");
                JsfUtils.postValidationError(JsfUtils.getFacesContext(), null, LabelUtils.getLabel("reminderUI.errorCreate", _localeContext.getLocale()));
            }
        } catch (Exception e) {
            LOG.error("Reminder[{}] save error", _reminder, e);
            JsfUtils.postValidationError(JsfUtils.getFacesContext(), null, LabelUtils.getLabel("reminderUI.errorCreate", _localeContext.getLocale()));
        }
    }


    public void btnReminderDialogDelete() {
        try {
            if (!_reminderDao.delete(null, _reminder)) {
                LogHelper.setEvent("id00220");
                JsfUtils.postValidationError(JsfUtils.getFacesContext(), null, LabelUtils.getLabel("msg.error.general", _localeContext.getLocale()));
            }
        } catch (Exception e) {
            LOG.error("Reminder[{}] delete error", _reminder, e);
            JsfUtils.postValidationError(JsfUtils.getFacesContext(), null, LabelUtils.getLabel("msg.error.general", _localeContext.getLocale()));
        }
    }


    public void btnReminderDialogCancel() {
        _reminder = null;
    }


    public void deleteReminder(Reminder reminder) {
        try {
            if (!_reminderDao.delete(null, reminder)) {
                LOG.error("Reminder[{}] delete error", reminder);
                JsfUtils.addErrorMessage(LabelUtils.getLabel("msg.error.general", _localeContext.getLocale()));
            }
        } catch (Exception e) {
            LOG.error("Reminder[{}] delete error", reminder, e);
            JsfUtils.addErrorMessage(LabelUtils.getLabel("msg.error.general", _localeContext.getLocale()));
        }
    }


    public void validatorRemTo(FacesContext fc, UIComponent component, Object data) throws ValidatorException {
        if (_reminder.getRecipients() == null || _reminder.getRecipients().isEmpty()) {
            JsfUtils.postValidationRequiredError(fc, component, (String) component.getAttributes().get("label"));
        }
    }


    /**
     * Builds RefItem based on Reminder data
     *
     * @param reminder
     * @return
     */
    public RefItem getRefDocItem(Reminder reminder) {
        return getRefDocItem(
                DaoUtils.getWorkflowDaoTenant(),
                reminder,
                _userContext.getAclContext(),
                _localeContext.getLocale()
        );
    }


    /**
     * Builds RefItem based on Reminder data
     *
     * @param workflowDao
     * @param reminder
     * @param aclContext
     * @param locale
     * @return
     */
    public static RefItem getRefDocItem(
            final WorkflowDao workflowDao,
            final Reminder reminder,
            final AclContext aclContext,
            final Locale locale) {

        RefItem refDoc = null;
        String refId = reminder.getRefId();
        if (isBlank(refId)) {
            return refDoc;
        }

        String remType = reminder.getRefType();
        if (Strings.CI.equals(remType, Workflow.class.getSimpleName())) {
            Workflow workflow = workflowDao.find(refId, aclContext);
            refDoc = new RefItem(RefItem.TYPE.WORKFLOW, refId, workflow, locale);
        } else {
            LOG.warn("Reminder[{}]: RefType is not defined for type: '{}'", reminder, remType);
            refDoc = new RefItem(RefItem.TYPE.WORKFLOW, refId, null, locale);
        }

        return refDoc;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public Reminder getReminderDS() {
        return _reminder;
    }


    public MongoLazyDataModel<Reminder> getReminderLazyDS() {
        if (_reminderLazyDS == null) {
            _reminderLazyDS = new MongoLazyDataModel<>(_reminderDao);
            _reminderLazyDS.addConstraintFilters("$or", List.of(
                    Map.of(Reminder._AUTHOR, _userContext.getId()),
                    Map.of(Reminder._RECIPIENTS, Map.of("$in", _userContext.getAclContext().toSetSysIdGroupsUnits()))
            ));
        }
        return _reminderLazyDS;
    }


    /**
     * Returns list of reminders set for this refId and available for the current user (excluding reminders set by current user)
     *
     * @return
     */
    public List<Reminder> getGroupReminders() {
        List<Reminder> result = _reminderDao.findAllByRefId(_userContext.getAclContext(), _refId, false);
        result.sort(Comparator.comparing(Reminder::getAt, Comparator.nullsLast(Comparator.naturalOrder())));
        //result.sort(Comparator.comparing(Reminder::getAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }


    public boolean hasOverdueReminders() {
        return _reminderDao.hasOverdue(_userContext.getAclContext());
    }


    public String getBtnId() {
        return _btnId;
    }


    public boolean isAllowDelete(Reminder reminder) {
        return Strings.CI.equals(reminder.getAuthor(), _userContext.getId());
    }
}
