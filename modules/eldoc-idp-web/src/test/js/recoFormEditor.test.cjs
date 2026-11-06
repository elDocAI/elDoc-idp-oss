/* SPDX-License-Identifier: AGPL-3.0-only */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/resources/eldoc/5_0_0/js/recoFormEditor.js'), 'utf8');

function loadRecoFormEditor(widgets = {}) {
    const missingWidgetLookups = [];
    const context = {
        EventTarget,
        PrimeFaces: {widgets},
        PF(widgetVar) {
            if (!widgets[widgetVar]) {
                missingWidgetLookups.push(widgetVar);
                throw new Error(`Unexpected PF lookup for missing widget '${widgetVar}'`);
            }
            return widgets[widgetVar];
        }
    };
    vm.createContext(context);
    vm.runInContext(source, context);
    return {context, missingWidgetLookups};
}

test('optional workflow and editor widgets can be absent without calling PF', () => {
    const {context, missingWidgetLookups} = loadRecoFormEditor();
    const optionalWidgetVars = [
        'coordsWV',
        'formTypeWV',
        'rfSiWV',
        'btnFldRemoveWV_0',
        'btnFldEditWV_0',
        'btnFldAddWV'
    ];

    optionalWidgetVars.forEach(widgetVar => {
        assert.equal(context.getPrimeFacesWidgetIfAvailable(widgetVar), null);
    });
    assert.deepEqual(missingWidgetLookups, []);
});

test('field-list end-of-list probes return absent widgets without calling PF', () => {
    const widgets = {
        btnFldRemoveWV_0: {name: 'remove-0'},
        btnFldEditWV_0: {name: 'edit-0'}
    };
    const {context, missingWidgetLookups} = loadRecoFormEditor(widgets);

    assert.equal(context.getPrimeFacesWidgetIfAvailable('btnFldRemoveWV_0'), widgets.btnFldRemoveWV_0);
    assert.equal(context.getPrimeFacesWidgetIfAvailable('btnFldEditWV_0'), widgets.btnFldEditWV_0);
    assert.equal(context.getPrimeFacesWidgetIfAvailable('btnFldRemoveWV_1'), null);
    assert.equal(context.getPrimeFacesWidgetIfAvailable('btnFldEditWV_1'), null);
    assert.deepEqual(missingWidgetLookups, []);
});

test('optional widget lookups see replacements after partial rendering', () => {
    const originalWidget = {name: 'original'};
    const replacementWidget = {name: 'replacement'};
    const widgets = {rfSiWV: originalWidget};
    const {context} = loadRecoFormEditor(widgets);

    assert.equal(context.getPrimeFacesWidgetIfAvailable('rfSiWV'), originalWidget);
    widgets.rfSiWV = replacementWidget;
    assert.equal(context.getPrimeFacesWidgetIfAvailable('rfSiWV'), replacementWidget);
});

test('workflow and editor call sites use safe lookups for conditional widgets and scans', () => {
    [
        "if (getPrimeFacesWidgetIfAvailable('coordsWV'))",
        'let formTypeWidget = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_FORM_TYPE);',
        'let formNameWidget = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_FORM_NAME_SI);',
        'let btnFldRemoveEL = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_PREFIX_BTN_FLD_REMOVE',
        'let btnFldEditEL = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_PREFIX_BTN_FLD_EDIT',
        'let addFieldButton = getPrimeFacesWidgetIfAvailable(RecoFormEditor.#WV_ID_BTN_FLD_ADD);',
        'let btnFldEditEL = getPrimeFacesWidgetIfAvailable(RecoFieldEditor.#WV_ID_COL_EDIT'
    ].forEach(callSite => assert.ok(source.includes(callSite), `Expected safe widget lookup: ${callSite}`));
});
