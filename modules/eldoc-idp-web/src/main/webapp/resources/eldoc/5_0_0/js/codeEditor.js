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

/* global CodeMirror */

// Array to store CodeMirror instances
const codeEditorInstances = [];

function initCodeEditor() {
    // Find all textarea elements with class 'code-editor'
    document.querySelectorAll('textarea.code-editor').forEach(function (editorNode, index) {
        // Skip already processed textareas
        // IMPORTANT: initCodeEditor function modifies DOM structure, so without skipping already processed elements in next calls
        // MutationObserver will be stuck in endless loop
        if (!editorNode.getAttribute("data-codeEditor-processed")) {
            let codeEditor = CodeMirror.fromTextArea(editorNode, {
                mode: {
                    name: editorNode.dataset.mode ?? "javascript",
                    globalVars: true
                },
                indentWithTabs: true,
                smartIndent: true,
                lineNumbers: true,
                matchBrackets: true,
                autoCloseBrackets: true,
                height: "auto",
                lineWrapping: editorNode.dataset.lineWrapping === "true",
                readOnly: editorNode.readOnly ? "nocursor" : false,
                extraKeys: {"Ctrl-Space": "autocomplete"}
            });

            // If textArea has size styles (width/height) - set corresponding sizes for codeEditor
            // Passing null to setSize methods tells codeEditor not to change corresponding property
            if (editorNode.style.width) {
                codeEditor.setSize(editorNode.style.width, null);
            }
            if (editorNode.style.height) {
                codeEditor.setSize(null, editorNode.style.height);
            }

            codeEditor.on('change', codeEditor.save);
            editorNode.setAttribute("data-codeEditor-processed", true);

            // Add the instance to the array
            codeEditorInstances.push(codeEditor);
        }
    });
}


function refreshCodeEditors() {
    try {
        codeEditorInstances.forEach((editor) => editor.refresh());
    } catch (e) {
        console.warn('Error refreshing CodeEditor', e);
    }
}


var observer = new MutationObserver(initCodeEditor);

// define what element should be observed by the observer
// and what types of mutations trigger the callback
observer.observe(document, {
    subtree: true,
    childList: true,
    attributes: false,
    characterData: false
});
