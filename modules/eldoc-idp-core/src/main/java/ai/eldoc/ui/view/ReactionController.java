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
import ai.eldoc.dao.ReactionDao;
import ai.eldoc.data.MongoLazyDataModel;
import ai.eldoc.model.Reaction;
import ai.eldoc.util.JsfUtils;
import ai.eldoc.util.LabelUtils;
import jakarta.faces.event.ActionEvent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.PrimeFaces;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;


/**
 * Reaction Controller
 */
@Named
@ViewScoped
public class ReactionController implements Serializable {

    @Serial
    private static final long serialVersionUID = 4657396641889033642L;
    public static final String COMPONENT_RENDERED = "uiReactionControl_rendered";

    @Inject
    private ReactionDao _reactionDao;

    @Inject
    private UserContext _userContext;

    @Inject
    private LocaleContext _localeContext;


    public enum ReactionOperation {
        ADD, DELETE, LIST
    }

    private Reaction _reaction;
    private String _to;
    private String _refId;
    private String _refreshId;
    private ReactionOperation _reactionOperation;
    MongoLazyDataModel<Reaction> _reactionsFromLazyDS;
    MongoLazyDataModel<Reaction> _reactionsToLazyDS;


    //
    // Methods ------------------------------------------------------------------------------
    //


    /**
     * Enables Exec Builder dialog
     *
     * @param ev
     */
    public void enableReactionDialog(jakarta.faces.event.ComponentSystemEvent ev) {
        if (!JsfUtils.getFacesContext().isPostback()) {
            JsfUtils.getViewMap().put(COMPONENT_RENDERED, true);
        }
    }


    public void reactionActionListener(ActionEvent event) {
        _refreshId = event.getComponent().getClientId();
        if (_refreshId.endsWith("rctLnk")) {
            _refreshId = _refreshId.replace("rctLnk", "respUser");
        } else if (_refreshId.endsWith("rctCnt")) {
            _refreshId = _refreshId.replace("rctCnt", "respUser");
        } else if (_refreshId.endsWith("userProfileLnk")) {
            _refreshId = _refreshId.replace("userProfileLnk", "userRctArea");
        }
    }


    public void giveReaction(String to, String refId, boolean giveReaction) {
        _reactionOperation = null;
        if (_userContext.getId().equals(to)) {
            return;
        }
        _to = to;
        _refId = refId;
        if (giveReaction || !isHaveReaction(_userContext.getId(), null, _refId)) {
            _reaction = new Reaction(_userContext.getId());
            _reaction.setTo(_to);
            if (_refId != null) {
                _reaction.setRef(_refId);
            }
            _reactionOperation = ReactionOperation.ADD;
        } else {
            _reactionOperation = ReactionOperation.DELETE;
        }

    }


    public void showReactionList(String to, String refId) {
        _to = to;
        _refId = refId;
        _reactionOperation = ReactionOperation.LIST;
    }


    public void saveReaction() {
        if (!_reactionDao.updateOrCreate(null, _reaction)) {
            JsfUtils.addErrorMessage(LabelUtils.getLabel("msg.error.general", _localeContext.getLocale()));
        }
        PrimeFaces.current().ajax().update(_refreshId);
    }


    public void deleteReaction(Reaction reaction) {
        _reactionDao.delete(null, reaction);
    }


    public void deleteReaction() {
        _reactionDao.deleteBy(_userContext.getId(), _to, _refId);
        PrimeFaces.current().ajax().update(_refreshId);
    }


    public void deleteReaction(String author, String to, String refId) {
        _reactionDao.deleteBy(author, to, refId);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public Reaction getReaction() {
        return _reaction;
    }


    public boolean isHaveReaction(String author, String to, String refId) {
        return _reactionDao.getCount(author, to, refId, false) > 0;
    }


    public long getReactionsWithCommentsCount(String author, String to, String refId) {
        return _reactionDao.getCount(author, to, refId, true);
    }


    public long getReactionsCount(String author, String to, String refId) {
        return _reactionDao.getCount(author, to, refId, false);
    }


    public MongoLazyDataModel<Reaction> getReactionByAuthorLazyDS(String author) {
        if (_reactionsFromLazyDS == null) {
            _reactionsFromLazyDS = new MongoLazyDataModel<>(_reactionDao);
            _reactionsFromLazyDS.addConstraintFilters(Reaction._AUTHOR, author);
        }
        return _reactionsFromLazyDS;
    }


    public MongoLazyDataModel<Reaction> getReactionToLazyDS(String to) {
        if (_reactionsToLazyDS == null) {
            _reactionsToLazyDS = new MongoLazyDataModel<>(_reactionDao);
            _reactionsToLazyDS.addConstraintFilters(Reaction._TO, to);
        }
        return _reactionsToLazyDS;
    }


    public List<Reaction> getReactionsList() {
        return _reactionDao.findBy(null, _to, _refId);
    }


    public ReactionOperation getReactionOperation() {
        return _reactionOperation;
    }


    public String getRefreshId() {
        return _refreshId;
    }

}
