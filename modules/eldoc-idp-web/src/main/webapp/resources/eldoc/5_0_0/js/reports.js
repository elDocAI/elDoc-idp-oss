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

/**
 * Converts string representation of duration in seconds (i.e., 1800s) to human-readable
 * format (i.e., 30m 0s), keeping only a specified number of meaningful parts (i.e., with
 * precision 2 - days and hours only, or hours and minutes)
 *
 * @param {Number} precision
 * @returns {String}
 */
String.prototype.toDurationLabel = function (precision) {
    const sec_num = parseInt(this, 10); // don't forget the second param
    const days = Math.floor(sec_num / (24 * 3600));
    const hours = Math.floor((sec_num - (days * 24 * 3600)) / 3600);
    const minutes = Math.floor((sec_num - (days * 24 * 3600) - (hours * 3600)) / 60);
    const seconds = sec_num - (days * 24 * 3600) - (hours * 3600) - (minutes * 60);

    const result = (days !== 0 ? (days + 'd ') : '')
            + ((days !== 0 || hours !== 0) ? (hours + 'h ') : '')
            + ((days !== 0 || hours !== 0 || minutes !== 0) ? (minutes + 'm ') : (''))
            + (seconds + 's');
    return result.split(" ").slice(0, precision).join(" ");
};

/**
 * Helper function to convert chart's tooltip, that store duration, to human-readable format
 *
 * @param {Object} context
 * @returns {String}
 */
const durationLabelingFunction = function (context) {
    return context.label + ': ' + context.raw.toString().toDurationLabel(2);
};

const systemBusyLabel = function (value) {
    return (value === 1 || value === "1") ? "Busy" : (value === 0 || value === "0") ? "Idle" : undefined;
};

function customizeCharts() {

    if (document.getElementById('reportData') === null || document.getElementById('reportData').value === "") {
        return;
    }

    const reportData = JSON.parse(document.getElementById('reportData').value);

    const reportType = reportData.reportType;

    // Customize IDP report charts
    if (reportType === 7) {
        let mainChart = PrimeFaces.widgets["mainChartWV"];
        if (mainChart) {
            mainChart.chart.config.options.scales.y.ticks.callback = systemBusyLabel;
            const timestampToDateFunction = function (value) {
                //"dd.MM.yyyy HH:mm:ss"
                const date = new Date(value);
                return (date.getDate() < 10 ? "0" : "") + date.getDate() +
                        "." + (date.getMonth() + 1 < 10 ? "0" : "") + (date.getMonth() + 1) +
                        "." + date.getFullYear() +
                        " " + (date.getHours() < 10 ? "0" : "") + date.getHours() +
                        ":" + (date.getMinutes() < 10 ? "0" : "") + date.getMinutes() +
                        ":" + (date.getSeconds() < 10 ? "0" : "") + date.getSeconds();
            };
            mainChart.chart.config.options.scales.x.ticks.callback = timestampToDateFunction;
            mainChart.chart.options.plugins.tooltip.callbacks = {
                label: (ctx) => timestampToDateFunction(ctx.dataset.data[ctx.dataIndex].x),
                title: (ctx) => systemBusyLabel(ctx[0].formattedValue)
            };
            mainChart.chart.update();
        }

        let secondaryChart = PrimeFaces.widgets["secondaryChartWV"];
        if (secondaryChart) {
            secondaryChart.chart.options.plugins.tooltip.callbacks = {
                label: durationLabelingFunction
            };
            secondaryChart.chart.update();
        }
    }

    // Add formating to charts that use 'duration'-type value
    if (reportType === 2 || reportType === 5 || reportType === 6) {
        let mainChart = PrimeFaces.widgets["mainChartWV"];
        if (mainChart) {
            mainChart.chart.config.options.scales.y.ticks.callback = function (value) {
                // We need to explicitly convert value to string, as at this step it is number
                return value.toString().toDurationLabel(2);
            };
            mainChart.chart.config.options.scales.y.ticks.stepSize = 15;
            mainChart.chart.options.plugins.tooltip.callbacks = {
                label: durationLabelingFunction
            };
            mainChart.chart.update();
        }
    }
}

let observer = new MutationObserver(customizeCharts);

// define what element should be observed by the observer
// and what types of mutations trigger the callback
observer.observe(document.querySelector("[id='f1:reportResultArea']").parentNode, {
    subtree: true,
    childList: true,
    attributes: false,
    characterData: true
});