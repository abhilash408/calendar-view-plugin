/*
 * The MIT License
 *
 * Copyright (c) 2018 Sven Schoenung
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import $ from 'jquery';
import moment from 'moment';
import tippy from 'tippy.js';

function head(event, options) {
  var $head = $('<div class="tooltip-head"></div>')
    .append(event.icon)
    .append($('<a class="tooltip-title"></a>').attr('href', event.url).text(event.title))
    .append($('<span class="tooltip-close">&#10006;</span>'));
  $head.find('.tooltip-close').click(options.close);
  return $head;
}

function body(event, view) {
  return $('<div class="tooltip-body"></div>')
    .append(left(event))
    .append(right(event, view))
    .append(bottom(event, view));
}

function left(event) {
  return $('<div class="tooltip-left"></div>')
    .append($('<b></b>').text(CalendarViewOptions.popupText.build))
    .append($('<div class="timestamp"></div>').text(event.timestampString))
    .append($('<div class="duration"></div>').text(event.durationString));
}

function right(event, view) {
  return (event.future) ? rightForFutureBuild(event, view) : rightForPastBuild(event, view);
}

function rightForPastBuild(event, view) {
  return $('<div class="tooltip-right"></div>')
    .append($('<b></b>').text(CalendarViewOptions.popupText.project))
    .append($('<div></div>')
      .append(event.job.icon)
      .append(' ')
      .append($('<a></a>').attr('href', event.job.url).text(event.job.title)))
    .append($('<div class="nextBuild"></div>')
      .append(event.nextScheduledBuild ? [CalendarViewOptions.popupText.nextBuild, dateLink(event.nextScheduledBuild, view)] : ''));
}

function rightForFutureBuild(event, view) {
  return $('<div class="tooltip-right"></div>')
    .append($('<b></b>').text(CalendarViewOptions.popupText.buildHistory))
    .append($('<ul></ul>').append((event.builds.length > 0) ? buildHistory(event, view) : noBuildHistory()));
}

function noBuildHistory() {
  return $('<li></li>').text(CalendarViewOptions.popupText.buildHistoryEmpty);
}

function buildHistory(event, view) {
  return event.builds.map(function(b) { return $('<li></li>').append(build(b, view)); });
}

function bottom(event, view) {
  if (event.future || (!event.previousBuild && !event.nextBuild)) {
    return '';
  }
  var $bottom = $('<div class="tooltip-bottom"></div>');
  if (event.previousBuild) {
    var bottomLeft = $('<span class="previous"></span>')
      .append('<i></i>')
      .append(build(event.previousBuild, view));
    $bottom.append(bottomLeft);
  }
  if (event.nextBuild) {
    var bottomRight = $('<span class="next"></span>')
      .append(build(event.nextBuild, view).reverse())
      .append('<i></i>');
    $bottom.append(bottomRight);
  }
  return $bottom.append('<div style="clear:both"></div>');
}

function build(build, view) {
  var $link = $('<a></a>').attr('href', build.url).text(build.title);
  var $dateLink = dateLink(build, view);
  return [build.icon, ' ', $link, ' ', $dateLink];
}

function dateLink(build, view) {
  var date = moment(build.start);
  var dateFormat = CalendarViewOptions.formats[view.type].popupBuildTimeFormat;
  var $dateLink = $('<a class="time" href="#"></a>');
  $dateLink.click(function() {
    closeAll();
    if (view.intervalStart.isSameOrBefore(date) && view.intervalEnd.isAfter(date)) {
      openForEventId(build.id, view);
    } else {
      CalendarViewOptions.gotoEventId = build.id;
      view.calendar.gotoDate(date);
    }
    return false;
  });
  return $dateLink.append($('<time></time>').text(date.format(dateFormat)));
}

export function dom(event, view, options) {
  return $('<div></div>')
    .append(head(event, options))
    .append(body(event, view));
}

export function openForEvent(event, view, target, manual) {
  var $popup = dom(event, view, {
    close: function() { popupInstance.hide(); }
  });

  var popupInstance = tippy.one(target, {
    html: $popup[0],
    arrow: true,
    animation: 'fade',
    interactive: true,
    theme: 'jenkins',
    trigger: manual ? 'manual' : 'mouseenter focus',
    size: 'large',
    onHidden: function() {
      popupInstance.destroy();
    }
  });
  if (manual) {
    popupInstance.show();
  }
}

export function openForEventId(eventId, view) {
  var event = view.calendar.clientEvents(function(e) { return e.id === eventId; })[0];
  var target = $('.event-id-' + eventId)[0];
  openForEvent(event, view, target, true);
}

export function closeAll() {
  $('*[data-tippy]').each(function(i, el) {
    el._tippy.destroy();
  });
}
