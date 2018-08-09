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
package io.jenkins.plugins.view.calendar;

import hudson.model.*;
import hudson.scheduler.CronTab;
import hudson.util.RunList;
import io.jenkins.plugins.view.calendar.util.DateUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class CalendarEventService {

    private CronJobService cronJobService;

    private Calendar now;
    private transient Calendar roundedNow;

    public CalendarEventService() {
        this(Calendar.getInstance());
    }

    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public CalendarEventService(final Calendar now) {
        this.cronJobService = new CronJobService(now);
        this.setNow(now);
    }

    public CronJobService getCronJobService() {
        return cronJobService;
    }

    public void setCronJobService(final CronJobService cronJobService) {
        this.cronJobService = cronJobService;
    }

    public Calendar getNow() {
        return now;
    }

    public void setNow(final Calendar now) {
        this.now = now;
        this.roundedNow = DateUtil.roundToNextMinute(now);
    }


    public List<CalendarEvent> getCalendarEvents(final List<TopLevelItem> items, final Calendar start, final Calendar end) {
        final List<CalendarEvent> events = new ArrayList<>();

        if (roundedNow.compareTo(start) < 0) {
            events.addAll(getFutureEvents(items, start, end));
        } else if (roundedNow.compareTo(end) > 0) {
            events.addAll(getPastEvents(items, start, end));
        } else {
            events.addAll(getPastEvents(items, start, roundedNow));
            events.addAll(getFutureEvents(items, now, end));
        }
        Collections.sort(events, new CalendarEventComparator());

        return events;
    }

    public List<CalendarEvent> getFutureEvents(final List<TopLevelItem> items, final Calendar start, final Calendar end) {
        final List<CalendarEvent> events = new ArrayList<CalendarEvent>();

        final Calendar ceilStart = DateUtil.roundToNextMinute(start);
        final Calendar floorStart = DateUtil.roundToPreviousMinute(start);
        floorStart.add(Calendar.SECOND, -1);

        final FutureEventCollector ceilEventCollector = new CeilEventCollector(events, ceilStart, end) ;
        final FutureEventCollector floorEventCollector = new FloorEventCollector(events, floorStart, end) ;

        for (final TopLevelItem item: items) {
            if (!(item instanceof AbstractProject)) {
                continue;
            }
            final long estimatedDuration = ((AbstractProject)item).getEstimatedDuration();
            final List<CronTab> cronTabs = cronJobService.getCronTabs(item);
            for (final CronTab cronTab: cronTabs) {
                ceilEventCollector.collectEvents(item, cronTab, estimatedDuration);
                floorEventCollector.collectEvents(item, cronTab, estimatedDuration);
            }
        }
        return events;
    }

    private abstract class FutureEventCollector {
        private transient final List<CalendarEvent> events;
        private transient final Calendar start;
        private transient final Calendar end;

        public FutureEventCollector(final List<CalendarEvent> events, final Calendar start, final Calendar end) {
            this.events = events;
            this.start = start;
            this.end = end;
        }

        public void collectEvents(final TopLevelItem item, final CronTab cronTab, final long estimatedDuration) {
            long timeInMillis = start.getTimeInMillis();
            do {
                final Calendar next = nextStart(cronTab, timeInMillis);
                if (next == null) {
                    break;
                }
                next.set(Calendar.SECOND, 0);
                next.set(Calendar.MILLISECOND, 0);
                @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                final CalendarEvent event = new CalendarEvent(item, next, estimatedDuration);
                if (!event.isInRange(start, end)) {
                    break;
                }
                events.add(event);
                timeInMillis = next.getTimeInMillis() + nextStartOffset();
            } while (true);
        }

        protected abstract Calendar nextStart(CronTab cronTab, long timeInMillis);

        protected abstract int nextStartOffset();
    }

    private class CeilEventCollector extends FutureEventCollector {
        public CeilEventCollector(final List<CalendarEvent> events, final Calendar start, final Calendar end) {
            super(events, start, end);
        }

        @Override
        protected Calendar nextStart(final CronTab cronTab, final long timeInMillis) {
            return cronTab.ceil(timeInMillis);
        }

        @Override
        protected int nextStartOffset() {
            return 1000 * 60;
        }
    }

    private class FloorEventCollector extends FutureEventCollector {
        public FloorEventCollector(final List<CalendarEvent> events, final Calendar start, final Calendar end) {
            super(events, start, end);
        }

        @Override
        protected Calendar nextStart(final CronTab cronTab, final long timeInMillis) {
            return cronTab.floor(timeInMillis);
        }

        @Override
        protected int nextStartOffset() {
            return -1000 * 60;
        }
    }

    public List<CalendarEvent> getPastEvents(final List<TopLevelItem> items, final Calendar start, final Calendar end) {
        final List<CalendarEvent> events = new ArrayList<CalendarEvent>();
        for (final TopLevelItem item: items) {
            if (!(item instanceof Job)) {
                continue;
            }
            final RunList<Run> builds = ((Job) item).getBuilds();
            for (final Run build : builds) {
                @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                final CalendarEvent event = new CalendarEvent(item, build);
                if (event.isInRange(start, end)) {
                    events.add(event);
                }
            }
        }
        return events;
    }

    public List<CalendarEvent> getLastEvents(final CalendarEvent event, final int numberOfEvents) {
        final List<CalendarEvent> lastEvents = new ArrayList<>();
        final TopLevelItem item = event.getItem();
        if (item instanceof Job) {
            final List<Run> lastBuilds = ((Job) item).getLastBuildsOverThreshold(numberOfEvents, Result.ABORTED);
            for (final Run lastBuild: lastBuilds) {
                @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                final CalendarEvent lastEvent = new CalendarEvent(item, lastBuild);
                lastEvents.add(lastEvent);
            }
        }
        return lastEvents;
    }

    public CalendarEvent getPreviousEvent(final CalendarEvent event) {
        if (event.getBuild() != null) {
            final Run previousBuild = event.getBuild().getPreviousBuild();
            if (previousBuild != null) {
                return new CalendarEvent(event.getItem(), previousBuild);
            }
        }
        return null;
    }

    public CalendarEvent getNextEvent(final CalendarEvent event) {
        if (event.getBuild() != null) {
            final Run nextBuild = event.getBuild().getNextBuild();
            if (nextBuild != null) {
                return new CalendarEvent(event.getItem(), nextBuild);
            }
        }
        return null;
    }

    public CalendarEvent getNextScheduledEvent(final CalendarEvent event) {
        final TopLevelItem item = event.getItem();
        if (!(item instanceof AbstractProject)) {
            return null;
        }
        final Calendar nextStart = cronJobService.getNextStart(item);
        if (nextStart != null) {
            final long estimatedDuration = ((AbstractProject)item).getEstimatedDuration();
            return new CalendarEvent(item, nextStart, estimatedDuration);
        }
        return null;
    }
}
