/* SPDX-License-Identifier: AGPL-3.0-only */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/resources/eldoc/5_0_0/js/eldoc.js'), 'utf8');

function classList(...initial) {
    const values = new Set(initial);
    return {
        contains: value => values.has(value),
        toggle(value, force = !values.has(value)) {
            if (force) values.add(value);
            else values.delete(value);
            return force;
        },
        add: value => values.add(value),
        remove: value => values.delete(value)
    };
}

function button() {
    return {
        classList: classList(),
        attributes: {},
        listeners: {},
        setAttribute(name, value) { this.attributes[name] = value; },
        addEventListener(name, listener) { this.listeners[name] = listener; }
    };
}

function loadLayout(hidden = false) {
    const menuButton = button();
    const mobileButton = button();
    const wrapper = { classList: classList(...(hidden ? ['layout-menu-static-inactive'] : [])) };
    const body = { classList: classList() };
    const requests = [];
    const context = {
        console,
        URL,
        $: () => ({ on() {} }),
        window: { location: { href: 'http://localhost/idp/start.xhtml' } },
        document: {
            head: { querySelector: () => ({ content: '' }) },
            body,
            addEventListener() {},
            getElementById: id => ({ 'menu-button': menuButton, 'topbar-menu-button': mobileButton })[id],
            querySelectorAll: () => [],
            querySelector: selector => selector === '.layout-wrapper' ? wrapper : null
        }
    };
    vm.createContext(context);
    vm.runInContext(source, context);
    context.doPost = (endpoint, data) => requests.push({ endpoint, data });
    return { context, menuButton, mobileButton, wrapper, body, requests };
}

function click(element) {
    let prevented = false;
    element.listeners.click({ currentTarget: element, preventDefault() { prevented = true; } });
    assert.equal(prevented, true);
}

test('desktop sidebar toggles immediately and persists the explicit preference', () => {
    const { context, menuButton, wrapper, requests } = loadLayout();
    context.registerEventListener_menuButton();
    assert.equal(menuButton.attributes['aria-expanded'], 'true');

    click(menuButton);
    assert.equal(wrapper.classList.contains('layout-menu-static-inactive'), true);
    assert.equal(menuButton.classList.contains('menu-button-rotate'), true);
    assert.equal(menuButton.attributes['aria-expanded'], 'false');
    assert.equal(requests[0].endpoint, '/api/uctx/cmd');
    assert.equal(requests[0].data.mainMenu, 'hide');

    click(menuButton);
    assert.equal(wrapper.classList.contains('layout-menu-static-inactive'), false);
    assert.equal(menuButton.attributes['aria-expanded'], 'true');
    assert.equal(requests[1].data.mainMenu, 'show');
});

test('saved hidden preference and mobile opening stay independent', () => {
    const { context, menuButton, mobileButton, wrapper, body, requests } = loadLayout(true);
    context.registerEventListener_menuButton();
    context.registerEventListener_topbarMenuButton();
    assert.equal(menuButton.attributes['aria-expanded'], 'false');

    click(mobileButton);
    assert.equal(body.classList.contains('mobile-menu-open'), true);
    assert.equal(mobileButton.attributes['aria-expanded'], 'true');
    assert.equal(wrapper.classList.contains('layout-menu-static-inactive'), true);
    assert.equal(requests.length, 0);

    click(mobileButton);
    assert.equal(body.classList.contains('mobile-menu-open'), false);
    assert.equal(mobileButton.attributes['aria-expanded'], 'false');
});